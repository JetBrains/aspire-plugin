package com.jetbrains.aspire.util

import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.ide.BrowserUtil
import com.intellij.notification.Notification
import com.intellij.notification.NotificationAction
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.EDT
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.trace
import com.intellij.openapi.project.Project
import com.jetbrains.aspire.AspireService
import com.intellij.platform.eel.EelApi
import com.intellij.platform.eel.isMac
import com.intellij.platform.eel.provider.getEelDescriptor
import com.intellij.platform.eel.provider.toEelApi
import com.intellij.platform.eel.provider.utils.awaitProcessResult
import com.intellij.platform.eel.provider.utils.stderrString
import com.intellij.platform.eel.provider.utils.stdoutString
import com.intellij.platform.eel.spawnProcess
import com.intellij.platform.ide.progress.withBackgroundProgress
import com.intellij.util.io.createDirectories
import com.jetbrains.aspire.AspireCoreBundle
import com.jetbrains.rider.run.configurations.runInRunToolWindow
import com.jetbrains.rider.runtime.RiderDotNetActiveRuntimeHost
import com.jetbrains.rider.web.DotNetSslCerts
import com.jetbrains.rider.web.RiderWebBundle
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.concurrency.await
import kotlin.io.path.absolutePathString
import kotlin.io.path.exists
import kotlin.io.path.pathString
import kotlin.io.path.readText

private val LOG = Logger.getInstance("#com.jetbrains.aspire.util.DevCertificateUtils")

private val json by lazy { Json { ignoreUnknownKeys = true } }

private const val CURRENT_ASPNET_CORE_CERTIFICATE_VERSION = 6
private const val MINIMUM_ASPNET_CORE_CERTIFICATE_VERSION = 4

@Suppress("unused")
private enum class DevCertificateTrustLevel {
    None,
    Partial,
    Full,
    Unknown;

    val isTrusted: Boolean
        get() = this == Partial || this == Full
}

@Serializable
private data class DevCertificate(
    @SerialName("Thumbprint") val thumbprint: String? = null,
    @SerialName("Version") val version: Int = 0,
    @SerialName("TrustLevel") val trustLevel: DevCertificateTrustLevel = DevCertificateTrustLevel.Unknown
)

@ApiStatus.Internal
sealed interface DevCertificateCheckResult {
    val isTrusted: Boolean

    data object Trusted : DevCertificateCheckResult {
        override val isTrusted = true
    }

    data object NoCertificate : DevCertificateCheckResult {
        override val isTrusted = false
    }

    data object NotTrusted : DevCertificateCheckResult {
        override val isTrusted = false
    }

    data object PartiallyTrusted : DevCertificateCheckResult {
        override val isTrusted = false
    }

    data class MultipleCertificatesIssue(
        val count: Int,
        val trustedCount: Int
    ) : DevCertificateCheckResult {
        override val isTrusted = trustedCount > 0 && trustedCount == count
    }

    data object CheckFailed : DevCertificateCheckResult {
        override val isTrusted = false
    }
}

private data class DevCertificateDiagnostics(
    val certificates: List<DevCertificate>,
    val result: DevCertificateCheckResult,
    val oldTrustedVersions: List<Int> = emptyList()
) {
    val requiresAttention: Boolean
        get() = !result.isTrusted || oldTrustedVersions.isNotEmpty()
}

@ApiStatus.Internal
@Suppress("UnstableApiUsage")
suspend fun checkDevCertificate(project: Project, showNotification: Boolean = false): DevCertificateCheckResult {
    val eelApi = project.getEelDescriptor().toEelApi()

    val diagnostics = collectDevCertificateDiagnostics(eelApi, project)

    if (diagnostics.oldTrustedVersions.isNotEmpty()) {
        LOG.warn(
            "Old trusted dev certificate versions detected: ${diagnostics.oldTrustedVersions.joinToString()}. " +
                    "Current version=$CURRENT_ASPNET_CORE_CERTIFICATE_VERSION, " +
                    "minimum supported version=$MINIMUM_ASPNET_CORE_CERTIFICATE_VERSION"
        )
    }

    val result = diagnostics.result
    LOG.trace { "Checking dev certificate result: $result" }

    if (showNotification && diagnostics.requiresAttention) {
        showNotification(project, diagnostics)
    }

    return result
}

@Suppress("UnstableApiUsage")
private suspend fun collectDevCertificateDiagnostics(eelApi: EelApi, project: Project): DevCertificateDiagnostics {
    val runtime = RiderDotNetActiveRuntimeHost.getInstance(project).dotNetCoreRuntime.value
    if (runtime == null) {
        LOG.debug("Unable to find any active dotnet runtime")
        return DevCertificateDiagnostics(emptyList(), DevCertificateCheckResult.CheckFailed)
    }

    return try {
        val process = eelApi.exec.spawnProcess(runtime.cliExePath.pathString)
            .args("dev-certs", "https", "--check-trust-machine-readable")
            .env(
                mapOf(
                    "DOTNET_NOLOGO" to "true",
                    "DOTNET_SKIP_FIRST_TIME_EXPERIENCE" to "true",
                    "DOTNET_CLI_TELEMETRY_OPTOUT" to "true",
                    "DOTNET_GENERATE_ASPNET_CERTIFICATE" to "false"
                )
            )
            .eelIt()
        val processResult = process.awaitProcessResult()
        if (processResult.exitCode != 0) {
            LOG.trace { "dotnet dev-certs check failed with exit code: ${processResult.exitCode}" }
            DevCertificateDiagnostics(emptyList(), DevCertificateCheckResult.CheckFailed)
        } else {
            val certificates = parseDevCertificateCheckOutput(processResult.stdoutString)
            analyzeCurrentCertificates(certificates)
        }
    } catch (ce: CancellationException) {
        throw ce
    } catch (e: Exception) {
        LOG.warn("Failed to check dev certificate", e)
        DevCertificateDiagnostics(emptyList(), DevCertificateCheckResult.CheckFailed)
    }
}

private fun parseDevCertificateCheckOutput(output: String): List<DevCertificate> {
    val start = output.indexOf('[')
    val end = output.lastIndexOf(']')
    if (start !in 0..<end) return emptyList()
    val jsonArray = output.substring(start, end + 1)
    return json.decodeFromString(jsonArray)
}

private fun analyzeCurrentCertificates(certificates: List<DevCertificate>): DevCertificateDiagnostics {
    if (certificates.isEmpty()) {
        return DevCertificateDiagnostics(emptyList(), DevCertificateCheckResult.NoCertificate)
    }

    val trustedCount = certificates.count { it.trustLevel.isTrusted }
    val fullyTrustedCount = certificates.count { it.trustLevel == DevCertificateTrustLevel.Full }
    val partiallyTrustedCount = certificates.count { it.trustLevel == DevCertificateTrustLevel.Partial }

    val result = when {
        certificates.size > 1 -> when (trustedCount) {
            certificates.size -> DevCertificateCheckResult.Trusted
            else -> DevCertificateCheckResult.MultipleCertificatesIssue(certificates.size, trustedCount)
        }

        trustedCount == 0 -> DevCertificateCheckResult.NotTrusted
        partiallyTrustedCount > 0 && fullyTrustedCount == 0 -> DevCertificateCheckResult.PartiallyTrusted
        else -> DevCertificateCheckResult.Trusted
    }

    val oldTrustedVersions = certificates
        .filter { it.trustLevel.isTrusted && it.version < CURRENT_ASPNET_CORE_CERTIFICATE_VERSION }
        .map { it.version }
        .distinct()
        .sorted()

    return DevCertificateDiagnostics(certificates, result, oldTrustedVersions)
}


private fun showNotification(project: Project, diagnostics: DevCertificateDiagnostics) {
    val notificationDescription = when (val result = diagnostics.result) {
        DevCertificateCheckResult.NoCertificate ->
            AspireCoreBundle.message("notification.dev.certificate.no.certificate")

        is DevCertificateCheckResult.NotTrusted ->
            AspireCoreBundle.message("notification.dev.certificate.not.trusted")

        DevCertificateCheckResult.PartiallyTrusted ->
            AspireCoreBundle.message("notification.dev.certificate.partially.trusted")

        is DevCertificateCheckResult.MultipleCertificatesIssue ->
            AspireCoreBundle.message("notification.dev.certificate.multiple", result.count, result.trustedCount)

        DevCertificateCheckResult.CheckFailed ->
            AspireCoreBundle.message("notification.dev.certificate.check.failed")

        DevCertificateCheckResult.Trusted ->
            AspireCoreBundle.message(
                "notification.dev.certificate.old.version",
                diagnostics.oldTrustedVersions.joinToString(),
                CURRENT_ASPNET_CORE_CERTIFICATE_VERSION,
                MINIMUM_ASPNET_CORE_CERTIFICATE_VERSION
            )
    }

    Notification(
        "Aspire",
        AspireCoreBundle.message("notification.dev.certificate.needs.attention"),
        notificationDescription,
        NotificationType.WARNING
    )
        .addDevCertificateActions(project, diagnostics)
        .notify(project)
}

private fun Notification.addDevCertificateActions(
    project: Project,
    diagnostics: DevCertificateDiagnostics
): Notification {
    if (diagnostics.result is DevCertificateCheckResult.NoCertificate ||
        diagnostics.result is DevCertificateCheckResult.NotTrusted
    ) {
        addAction(trustDevCertificateAction(project))
    }

    if ((diagnostics.result is DevCertificateCheckResult.Trusted && diagnostics.oldTrustedVersions.isNotEmpty()) ||
        diagnostics.result is DevCertificateCheckResult.MultipleCertificatesIssue
    ) {
        addAction(cleanAndTrustDevCertificateAction(project))
    }


    if (diagnostics.result is DevCertificateCheckResult.PartiallyTrusted) {
        addAction(object : NotificationAction(AspireCoreBundle.message("notification.dev.certificate.learn.more")) {
            override fun actionPerformed(e: AnActionEvent, notification: Notification) {
                BrowserUtil.browse("https://learn.microsoft.com/en-us/aspnet/core/security/enforcing-ssl#linux-specific-considerations")
            }
        })
    } else {
        addAction(object : NotificationAction(AspireCoreBundle.message("notification.dev.certificate.learn.more")) {
            override fun actionPerformed(e: AnActionEvent, notification: Notification) {
                BrowserUtil.browse("https://learn.microsoft.com/en-us/aspnet/core/security/enforcing-ssl#trust-the-aspnet-core-https-development-certificate")
            }
        })
    }

    return this
}

@Suppress("UnstableApiUsage")
private fun trustDevCertificateAction(project: Project): NotificationAction {
    return object : NotificationAction(AspireCoreBundle.message("notification.dev.certificate.action.trust")) {
        override fun actionPerformed(e: AnActionEvent, notification: Notification) {
            notification.expire()
            e.coroutineScope.launch(Dispatchers.Default) {
                runDevCertificateCommands(
                    project,
                    AspireCoreBundle.message("progress.trusting.dev.certificate"),
                    listOf("--trust")
                )
            }
        }
    }
}

@Suppress("UnstableApiUsage")
private fun cleanAndTrustDevCertificateAction(project: Project): NotificationAction {
    return object :
        NotificationAction(AspireCoreBundle.message("notification.dev.certificate.action.clean.and.trust")) {
        override fun actionPerformed(e: AnActionEvent, notification: Notification) {
            notification.expire()
            e.coroutineScope.launch(Dispatchers.Default) {
                runDevCertificateCommands(
                    project,
                    AspireCoreBundle.message("progress.cleaning.and.trusting.dev.certificate"),
                    listOf("--clean", "--trust")
                )
            }
        }
    }
}

@Suppress("UnstableApiUsage")
private suspend fun runDevCertificateCommands(project: Project, progressTitle: String, commands: List<String>) {
    val runtime = RiderDotNetActiveRuntimeHost.getInstance(project).dotNetCoreRuntime.value
    if (runtime == null) {
        notifyDevCertificateCommandFailure(
            project,
            AspireCoreBundle.message("notification.dev.certificate.runtime.not.available")
        )
        return
    }

    val eelApi = project.getEelDescriptor().toEelApi()
    val environment = mapOf(
        "DOTNET_NOLOGO" to "true",
        "DOTNET_SKIP_FIRST_TIME_EXPERIENCE" to "true",
        "DOTNET_CLI_TELEMETRY_OPTOUT" to "true"
    )

    val succeeded = try {
        if (eelApi.platform.isMac) {
            runDevCertificateCommandsInRunToolWindow(
                project,
                progressTitle,
                runtime.cliExePath.pathString,
                commands,
                environment
            )
        } else {
            runDevCertificateCommandsWithEel(
                project,
                eelApi,
                progressTitle,
                runtime.cliExePath.pathString,
                commands,
                environment
            )
        }
    } catch (ce: CancellationException) {
        throw ce
    } catch (e: Exception) {
        LOG.warn("Unable to update HTTPS development certificates", e)
        false
    }

    if (succeeded) {
        notifyDevCertificateCommandSuccess(project)
    } else {
        notifyDevCertificateCommandFailure(
            project,
            AspireCoreBundle.message("notification.dev.certificate.command.failed")
        )
    }
}

@Suppress("UnstableApiUsage")
private suspend fun runDevCertificateCommandsWithEel(
    project: Project,
    eelApi: EelApi,
    progressTitle: String,
    executablePath: String,
    commands: List<String>,
    environment: Map<String, String>
): Boolean = withBackgroundProgress(project, progressTitle) {
    for (command in commands) {
        val allArgs = listOf("dev-certs", "https", command)
        LOG.trace { "Running dev certificate command: $executablePath ${allArgs.joinToString(" ")}" }
        val process = eelApi.exec.spawnProcess(executablePath)
            .args(allArgs)
            .env(environment)
            .eelIt()
        val executionResult = process.awaitProcessResult()

        if (executionResult.exitCode != 0) {
            val outString = executionResult.stdoutString
            val errString = executionResult.stderrString
            LOG.warn("Unable to update HTTPS development certificates; stdout: $outString; stderr: $errString")
            return@withBackgroundProgress false
        }
    }

    true
}

private suspend fun runDevCertificateCommandsInRunToolWindow(
    project: Project,
    progressTitle: String,
    executablePath: String,
    commands: List<String>,
    environment: Map<String, String>
): Boolean {
    val lifetimeDef = AspireService.getInstance(project).lifetime.createNested()

    try {
        for (command in commands) {
            val allArgs = listOf("dev-certs", "https", command)
            val commandLine = createDevCertificateCommandLine(executablePath, allArgs, environment)
            LOG.trace { "Running dev certificate command in Run tool window: $commandLine" }

            val exitCode = commandLine.runInRunToolWindow(project, lifetimeDef.lifetime, progressTitle, LOG).await()
            if (exitCode != 0) {
                LOG.warn("Unable to update HTTPS development certificates; exitCode=$exitCode")
                return false
            }
        }

        return true
    } finally {
        lifetimeDef.terminate()
    }
}

private fun createDevCertificateCommandLine(
    executablePath: String,
    arguments: List<String>,
    environment: Map<String, String>
): GeneralCommandLine =
    GeneralCommandLine()
        .withExePath(executablePath)
        .withParameters(arguments)
        .withEnvironment(environment)

private suspend fun notifyDevCertificateCommandFailure(project: Project, details: String) =
    withContext(Dispatchers.EDT) {
        Notification(
            "Aspire",
            details,
            "",
            NotificationType.WARNING
        ).notify(project)
    }

private suspend fun notifyDevCertificateCommandSuccess(project: Project) =
    withContext(Dispatchers.EDT) {
        Notification(
            "Aspire",
            AspireCoreBundle.message("notification.dev.certificate.updated"),
            "",
            NotificationType.INFORMATION
        ).notify(project)
    }


@Suppress("UnstableApiUsage")
internal suspend fun exportCertificate(project: Project): String? {
    val runtime = RiderDotNetActiveRuntimeHost.getInstance(project).dotNetCoreRuntime.value ?: return null

    val certificateFolder = DotNetSslCerts.getAspNetCoreCertificateFolder()
    if (!certificateFolder.exists()) {
        certificateFolder.createDirectories()
    }

    val certificateFile = certificateFolder.resolve("aspire-worker.crt")

    val eelApi = project.getEelDescriptor().toEelApi()
    val exitCode =
        withBackgroundProgress(project, RiderWebBundle.message("DotNetSslCerts.progress.title.certificate.export")) {
            val process = eelApi.exec.spawnProcess(runtime.cliExePath.pathString)
                .args("dev-certs", "https", "--export-path", certificateFile.absolutePathString(), "--format", "PEM")
                .env(
                    mapOf(
                        "DOTNET_NOLOGO" to "true",
                        "DOTNET_SKIP_FIRST_TIME_EXPERIENCE" to "true",
                        "DOTNET_CLI_TELEMETRY_OPTOUT" to "true",
                        "DOTNET_GENERATE_ASPNET_CERTIFICATE" to "false"
                    )
                )
                .eelIt()
            val processResult = process.awaitProcessResult()
            processResult.exitCode
        }

    if (exitCode != 0 || !certificateFile.exists()) {
        LOG.info("Failed to export certificate, exitCode=$exitCode")
        return null
    }

    val text = certificateFile.readText()

    // Remove PEM header and footer
    val cleaned = text
        .removePrefix("-----BEGIN CERTIFICATE-----")
        .removeSuffix("-----END CERTIFICATE-----")
        .lines()
        .joinToString("")
        .trim()

    return cleaned
}
