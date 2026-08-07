package com.jetbrains.aspire.rider.run

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
import com.intellij.platform.eel.EelApi
import com.intellij.platform.eel.fs.createTemporaryFile
import com.intellij.platform.eel.getOrNull
import com.intellij.platform.eel.isMac
import com.intellij.platform.eel.provider.asNioPath
import com.intellij.platform.eel.provider.getEelDescriptor
import com.intellij.platform.eel.provider.toEelApi
import com.intellij.platform.eel.provider.utils.awaitProcessResult
import com.intellij.platform.eel.provider.utils.stderrString
import com.intellij.platform.eel.provider.utils.stdoutString
import com.intellij.platform.eel.spawnProcess
import com.intellij.platform.ide.progress.withBackgroundProgress
import com.jetbrains.aspire.AspireCoreBundle
import com.jetbrains.aspire.AspireService
import com.jetbrains.aspire.certificates.DevCertificateAnalyzer
import com.jetbrains.aspire.certificates.DevCertificateDiagnostics
import com.jetbrains.aspire.certificates.DevCertificateKeyMaterial
import com.jetbrains.aspire.extensions.DevCertificateCheckResult
import com.jetbrains.rider.environment.initializeAndGetEnvironment
import com.jetbrains.rider.run.configurations.runInRunToolWindow
import com.jetbrains.rider.runtime.RiderDotNetActiveRuntimeHost
import com.jetbrains.rider.web.RiderWebBundle
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.concurrency.await
import java.nio.file.Path
import java.security.KeyStore
import java.util.*
import kotlin.io.path.*

private val LOG = Logger.getInstance("#com.jetbrains.aspire.util.DevCertificateUtils")

@Suppress("UnstableApiUsage")
internal suspend fun checkDevCertificate(
    useBundledRuntime: Boolean,
    project: Project,
    showNotification: Boolean = false
): DevCertificateCheckResult {
    val eelApi = project.getEelDescriptor().toEelApi()

    val diagnostics = collectDevCertificateDiagnostics(eelApi, useBundledRuntime, project)

    if (diagnostics.oldTrustedVersions.isNotEmpty()) {
        LOG.warn(
            "Old trusted dev certificate versions detected: ${diagnostics.oldTrustedVersions.joinToString()}. " +
                    "Current version=${DevCertificateAnalyzer.CURRENT_ASPNET_CORE_CERTIFICATE_VERSION}, " +
                    "minimum supported version=${DevCertificateAnalyzer.MINIMUM_ASPNET_CORE_CERTIFICATE_VERSION}"
        )
    }

    val result = diagnostics.result
    LOG.trace { "Checking dev certificate result: $result" }

    if (showNotification && diagnostics.requiresAttention) {
        showNotification(useBundledRuntime, project, diagnostics)
    }

    return result
}

@Suppress("UnstableApiUsage")
private suspend fun collectDevCertificateDiagnostics(
    eelApi: EelApi,
    useBundledRuntime: Boolean,
    project: Project
): DevCertificateDiagnostics {
    val dotnetCliPath = if (useBundledRuntime) {
        project.initializeAndGetEnvironment().getRuntime().cliPath()
    } else {
        val runtime = RiderDotNetActiveRuntimeHost.getInstance(project).dotNetCoreRuntime.value
        if (runtime == null) {
            LOG.debug("Unable to find any active dotnet runtime")
            return DevCertificateDiagnostics(emptyList(), DevCertificateCheckResult.CheckFailed)
        }
        runtime.cliExePath
    }

    return try {
        val process = eelApi.exec.spawnProcess(dotnetCliPath.pathString)
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
            DevCertificateAnalyzer.getInstance().analyze(processResult.stdoutString)
        }
    } catch (ce: CancellationException) {
        throw ce
    } catch (e: Exception) {
        LOG.warn("Failed to check dev certificate", e)
        DevCertificateDiagnostics(emptyList(), DevCertificateCheckResult.CheckFailed)
    }
}

private fun showNotification(useBundledRuntime: Boolean, project: Project, diagnostics: DevCertificateDiagnostics) {
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
                DevCertificateAnalyzer.CURRENT_ASPNET_CORE_CERTIFICATE_VERSION,
                DevCertificateAnalyzer.MINIMUM_ASPNET_CORE_CERTIFICATE_VERSION
            )
    }

    Notification(
        "Aspire",
        AspireCoreBundle.message("notification.dev.certificate.needs.attention"),
        notificationDescription,
        NotificationType.WARNING
    )
        .addDevCertificateActions(useBundledRuntime, project, diagnostics)
        .notify(project)
}

private fun Notification.addDevCertificateActions(
    useBundledRuntime: Boolean,
    project: Project,
    diagnostics: DevCertificateDiagnostics
): Notification {
    if (diagnostics.result is DevCertificateCheckResult.NoCertificate ||
        diagnostics.result is DevCertificateCheckResult.NotTrusted
    ) {
        addAction(trustDevCertificateAction(useBundledRuntime, project))
    }

    if ((diagnostics.result is DevCertificateCheckResult.Trusted && diagnostics.oldTrustedVersions.isNotEmpty()) ||
        diagnostics.result is DevCertificateCheckResult.MultipleCertificatesIssue
    ) {
        addAction(cleanAndTrustDevCertificateAction(useBundledRuntime, project))
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

private fun trustDevCertificateAction(useBundledRuntime: Boolean, project: Project): NotificationAction {
    return object : NotificationAction(AspireCoreBundle.message("notification.dev.certificate.action.trust")) {
        override fun actionPerformed(e: AnActionEvent, notification: Notification) {
            notification.expire()
            e.coroutineScope.launch(Dispatchers.Default) {
                runDevCertificateCommands(
                    useBundledRuntime,
                    project,
                    AspireCoreBundle.message("progress.trusting.dev.certificate"),
                    listOf("--trust")
                )
            }
        }
    }
}

private fun cleanAndTrustDevCertificateAction(useBundledRuntime: Boolean, project: Project): NotificationAction {
    return object :
        NotificationAction(AspireCoreBundle.message("notification.dev.certificate.action.clean.and.trust")) {
        override fun actionPerformed(e: AnActionEvent, notification: Notification) {
            notification.expire()
            e.coroutineScope.launch(Dispatchers.Default) {
                runDevCertificateCommands(
                    useBundledRuntime,
                    project,
                    AspireCoreBundle.message("progress.cleaning.and.trusting.dev.certificate"),
                    listOf("--clean", "--trust")
                )
            }
        }
    }
}

@Suppress("UnstableApiUsage")
private suspend fun runDevCertificateCommands(
    useBundledRuntime: Boolean,
    project: Project,
    progressTitle: String,
    commands: List<String>
) {
    val dotnetCliPath = if (useBundledRuntime) {
        project.initializeAndGetEnvironment().getRuntime().cliPath()
    } else {
        val runtime = RiderDotNetActiveRuntimeHost.getInstance(project).dotNetCoreRuntime.value
        if (runtime == null) {
            notifyDevCertificateCommandFailure(
                project,
                AspireCoreBundle.message("notification.dev.certificate.runtime.not.available")
            )
            return
        }
        runtime.cliExePath
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
                dotnetCliPath.pathString,
                commands,
                environment
            )
        } else {
            runDevCertificateCommandsWithEel(
                project,
                eelApi,
                progressTitle,
                dotnetCliPath.pathString,
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


private enum class DevCertificateExportFormat(val argument: String, val extension: String) {
    Pem("PEM", "pem"),
    Pfx("PFX", "pfx")
}

/**
 * Exports the public development certificate as base64-encoded DER, the form DCP expects in
 * `DEBUG_SESSION_SERVER_CERTIFICATE`.
 */
@Suppress("UnstableApiUsage")
internal suspend fun exportDevCertificateAndReadFile(
    useBundledRuntime: Boolean,
    project: Project
): Result<String> {
    val eelApi = project.getEelDescriptor().toEelApi()
    val certificateFile = eelApi.fs.createTemporaryFile()
        .prefix("aspire-dev-cert")
        .suffix(".${DevCertificateExportFormat.Pem.extension}")
        .deleteOnExit(true)
        .eelIt()
        .getOrNull()
        ?.asNioPath()
    if (certificateFile == null) {
        LOG.warn("Unable to create a temporary pem file for certificate exporting")
        return Result.failure(Exception("Unable to create a temporary pem file for certificate exporting"))
    }

    val exportResult = exportDevCertificate(
        useBundledRuntime,
        project,
        certificateFile,
        DevCertificateExportFormat.Pem
    )
    if (!exportResult.isSuccess) {
        return Result.failure(requireNotNull(exportResult.exceptionOrNull()))
    }

    // Remove PEM header and footer
    val content = certificateFile
        .readText()
        .removePrefix("-----BEGIN CERTIFICATE-----")
        .removeSuffix("-----END CERTIFICATE-----")
        .lines()
        .joinToString("")
        .trim()
    return Result.success(content)
}

/**
 * Exports the development certificate together with its private key and loads it into an in-memory
 * PKCS12 key store, so the IDE can terminate TLS with it.
 */
@Suppress("UnstableApiUsage")
internal suspend fun exportDevCertificateAndLoadToKeyStore(
    useBundledRuntime: Boolean,
    project: Project
): Result<DevCertificateKeyMaterial> {
    val eelApi = project.getEelDescriptor().toEelApi()
    val certificateFile = eelApi.fs.createTemporaryFile()
        .prefix("aspire-dev-cert")
        .suffix(".${DevCertificateExportFormat.Pfx.extension}")
        .deleteOnExit(true)
        .eelIt()
        .getOrNull()
        ?.asNioPath()
    if (certificateFile == null) {
        LOG.warn("Unable to create a temporary pfx file for certificate exporting")
        return Result.failure(Exception("Unable to create a temporary pfx file for certificate exporting"))
    }

    val password = UUID.randomUUID().toString()
    val exportResult = exportDevCertificate(
        useBundledRuntime,
        project,
        certificateFile,
        DevCertificateExportFormat.Pfx,
        password
    )
    if (!exportResult.isSuccess) {
        return Result.failure(requireNotNull(exportResult.exceptionOrNull()))
    }

    val keyStore = KeyStore.getInstance("PKCS12")
    certificateFile.inputStream().use {
        keyStore.load(it, password.toCharArray())
    }

    val alias = keyStore.aliases().asSequence().firstOrNull { keyStore.isKeyEntry(it) }
    if (alias == null) {
        LOG.warn("Exported PKCS12 key store has no private key entry")
        return Result.failure(Exception("Exported PKCS12 key store has no private key entry"))
    }

    val material = DevCertificateKeyMaterial(keyStore, alias, password.toCharArray())
    return Result.success(material)
}

@Suppress("UnstableApiUsage")
private suspend fun exportDevCertificate(
    useBundledRuntime: Boolean,
    project: Project,
    certificateFile: Path,
    format: DevCertificateExportFormat,
    password: String? = null
): Result<Unit> {
    val dotnetCliPath = if (useBundledRuntime) {
        project.initializeAndGetEnvironment().getRuntime().cliPath()
    } else {
        RiderDotNetActiveRuntimeHost.getInstance(project).dotNetCoreRuntime.value?.cliExePath
    }
    if (dotnetCliPath == null) {
        return Result.failure(Exception("Unable to find .NET CLI runtime"))
    }

    val eelApi = project.getEelDescriptor().toEelApi()
    val exitCode =
        withBackgroundProgress(project, RiderWebBundle.message("DotNetSslCerts.progress.title.certificate.export")) {
            val args = buildList {
                add("dev-certs")
                add("https")
                add("--export-path")
                add(certificateFile.absolutePathString())
                add("--format")
                add(format.argument)
                if (password != null) {
                    add("--password")
                    add(password)
                }
            }
            val process = eelApi.exec.spawnProcess(dotnetCliPath.pathString)
                .args(args)
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
        return Result.failure(Exception("Failed to export certificate"))
    }

    return Result.success(Unit)
}
