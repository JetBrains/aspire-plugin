package com.jetbrains.rider.aspire.util

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.jetbrains.rd.util.lifetime.Lifetime
import com.jetbrains.rider.run.configurations.runWithProgress
import com.jetbrains.rider.runtime.RiderDotNetActiveRuntimeHost
import com.jetbrains.rider.web.DotNetSslCerts
import com.jetbrains.rider.web.RiderWebBundle
import org.jetbrains.concurrency.await
import kotlin.io.path.absolutePathString
import kotlin.io.path.exists
import kotlin.io.path.readText

private val LOG = Logger.getInstance("#com.jetbrains.rider.aspire.util.DevCertificateUtils")

internal suspend fun checkDevCertificate(lifetime: Lifetime, project: Project): Boolean {
    val runtime = RiderDotNetActiveRuntimeHost.getInstance(project).dotNetCoreRuntime.value ?: return false
    return DotNetSslCerts.getInstance(project).checkCommandAsync(lifetime, runtime).await()
}

internal suspend fun exportCertificate(lifetime: Lifetime, project: Project): String? {
    val runtime = RiderDotNetActiveRuntimeHost.getInstance(project).dotNetCoreRuntime.value ?: return null
    val certificateFile = DotNetSslCerts.getAspNetCoreCertificateFolder().resolve("aspire-worker.crt")

    if (!certificateFile.exists()) {
        val commandLine = runtime.createCommandLine(
            listOf(
                "dev-certs",
                "https",
                "--export-path",
                certificateFile.absolutePathString(),
                "--format",
                "PEM"
            )
        )
        val exitCode = commandLine.runWithProgress(
            project,
            lifetime,
            RiderWebBundle.message("DotNetSslCerts.progress.title.certificate.export"),
            LOG
        ).await()

        if (exitCode != 0 || !certificateFile.exists()) {
            LOG.info("Failed to export certificate, exitCode=$exitCode")
            return null
        }
    }

    val text = certificateFile.readText()

    // Remove PEM header and footer
    val cleaned = text
        .removePrefix("-----BEGIN CERTIFICATE-----")
        .removeSuffix("-----END CERTIFICATE-----")
        .trim()

    return cleaned
}