@file:Suppress("UnstableApiUsage")

package com.jetbrains.aspire.rider.run

import com.intellij.ide.BrowserUtil
import com.intellij.notification.Notification
import com.intellij.notification.NotificationAction
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.EDT
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.trace
import com.intellij.openapi.project.Project
import com.intellij.platform.eel.fs.createTemporaryFile
import com.intellij.platform.eel.getOrNull
import com.intellij.platform.eel.provider.asNioPath
import com.intellij.platform.eel.provider.getEelDescriptor
import com.intellij.platform.eel.provider.toEelApi
import com.intellij.platform.ide.progress.withBackgroundProgress
import com.jetbrains.aspire.AspireCoreBundle
import com.jetbrains.aspire.AspireService
import com.jetbrains.aspire.certificates.DevCertificateAnalyzer
import com.jetbrains.aspire.certificates.DevCertificateCheckResult
import com.jetbrains.aspire.certificates.DevCertificateDiagnostics
import com.jetbrains.aspire.certificates.DevCertificateKeyMaterial
import com.jetbrains.rider.environment.initializeAndGetEnvironment
import com.jetbrains.rider.runtime.RiderDotNetActiveRuntimeHost
import com.jetbrains.rider.web.DevCertificateService
import com.jetbrains.rider.web.RiderWebBundle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.nio.file.Path
import java.security.KeyStore
import java.util.*
import kotlin.io.path.exists
import kotlin.io.path.inputStream
import kotlin.io.path.readText

private val LOG = Logger.getInstance("#com.jetbrains.aspire.util.DevCertificateUtils")

internal suspend fun checkDevCertificate(
    useBundledRuntime: Boolean,
    project: Project,
    showNotification: Boolean = false
): DevCertificateCheckResult = withContext(Dispatchers.Default) {
    val diagnostics = collectDevCertificateDiagnostics(useBundledRuntime, project)

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

    return@withContext result
}

@Suppress("UnstableApiUsage")
private suspend fun collectDevCertificateDiagnostics(
    useBundledRuntime: Boolean,
    project: Project
): DevCertificateDiagnostics {
    val dotnetCliPath = getDotnetCliPath(useBundledRuntime, project)
        ?: return DevCertificateDiagnostics(emptyList(), DevCertificateCheckResult.CheckFailed)

    val devCertificates = DevCertificateService
        .getInstance(project)
        .checkTrustedDevCertificatesWithOutput(dotnetCliPath)
        .getOrElse {
            LOG.debug("Unable to check trusted dev certificates")
            return DevCertificateDiagnostics(emptyList(), DevCertificateCheckResult.CheckFailed)
        }

    return DevCertificateAnalyzer.getInstance().analyze(devCertificates)
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
                trustDevCertificate(false, useBundledRuntime, project)
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
                trustDevCertificate(true, useBundledRuntime, project)
            }
        }
    }
}

private suspend fun trustDevCertificate(clean: Boolean, useBundledRuntime: Boolean, project: Project) {
    val cliPath = getDotnetCliPath(useBundledRuntime, project)
    if (cliPath == null) {
        notifyDevCertificateCommandFailure(
            project,
            AspireCoreBundle.message("notification.dev.certificate.runtime.not.available")
        )
        return
    }
    val lifetimeDef = AspireService.getInstance(project).lifetime.createNested()
    try {
        val result = DevCertificateService
            .getInstance(project)
            .trustDevCertificate(cliPath, clean, lifetimeDef.lifetime)
        if (result.isSuccess) {
            notifyDevCertificateCommandSuccess(project)
        } else {
            notifyDevCertificateCommandFailure(
                project,
                AspireCoreBundle.message("notification.dev.certificate.command.failed")
            )
        }
    } finally {
        lifetimeDef.terminate()
    }
}

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
): Result<String> = withContext(Dispatchers.Default) {
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
        return@withContext Result.failure(Exception("Unable to create a temporary pem file for certificate exporting"))
    }

    exportDevCertificate(
        useBundledRuntime,
        project,
        certificateFile,
        DevCertificateExportFormat.Pem
    ).onFailure {
        return@withContext Result.failure(it)
    }

    // Remove PEM header and footer
    val content = certificateFile
        .readText()
        .removePrefix("-----BEGIN CERTIFICATE-----")
        .removeSuffix("-----END CERTIFICATE-----")
        .lines()
        .joinToString("")
        .trim()
    return@withContext Result.success(content)
}

/**
 * Exports the development certificate together with its private key and loads it into an in-memory
 * PKCS12 key store, so the IDE can terminate TLS with it.
 */
@Suppress("UnstableApiUsage")
internal suspend fun exportDevCertificateAndLoadToKeyStore(
    useBundledRuntime: Boolean,
    project: Project
): Result<DevCertificateKeyMaterial> = withContext(Dispatchers.Default) {
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
        return@withContext Result.failure(Exception("Unable to create a temporary pfx file for certificate exporting"))
    }

    val password = UUID.randomUUID().toString()
    exportDevCertificate(
        useBundledRuntime,
        project,
        certificateFile,
        DevCertificateExportFormat.Pfx,
        password
    ).onFailure {
        return@withContext Result.failure(it)
    }

    val keyStore = KeyStore.getInstance("PKCS12")
    certificateFile.inputStream().use {
        keyStore.load(it, password.toCharArray())
    }

    val alias = keyStore.aliases().asSequence().firstOrNull { keyStore.isKeyEntry(it) }
    if (alias == null) {
        LOG.warn("Exported PKCS12 key store has no private key entry")
        return@withContext Result.failure(Exception("Exported PKCS12 key store has no private key entry"))
    }

    val material = DevCertificateKeyMaterial(keyStore, alias, password.toCharArray())
    return@withContext Result.success(material)
}

@Suppress("UnstableApiUsage")
private suspend fun exportDevCertificate(
    useBundledRuntime: Boolean,
    project: Project,
    certificateFile: Path,
    format: DevCertificateExportFormat,
    password: String? = null
): Result<Unit> {
    val dotnetCliPath = getDotnetCliPath(useBundledRuntime, project)
        ?: return Result.failure(Exception("Unable to find .NET CLI runtime"))

    val result =
        withBackgroundProgress(project, RiderWebBundle.message("DotNetSslCerts.progress.title.certificate.export")) {
            DevCertificateService
                .getInstance(project)
                .exportDevCertificate(dotnetCliPath, certificateFile, password, format.argument)
        }

    if (result.isFailure || !certificateFile.exists()) {
        LOG.info("Failed to export certificate")
        return Result.failure(Exception("Failed to export certificate"))
    }

    return Result.success(Unit)
}

private suspend fun getDotnetCliPath(useBundledRuntime: Boolean, project: Project): Path? {
    return if (useBundledRuntime) {
        project.initializeAndGetEnvironment().getRuntime().cliPath()
    } else {
        RiderDotNetActiveRuntimeHost.getInstance(project).dotNetCoreRuntime.value?.cliExePath
    }
}
