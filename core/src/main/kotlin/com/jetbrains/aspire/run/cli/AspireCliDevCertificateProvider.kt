@file:Suppress("UnstableApiUsage")

package com.jetbrains.aspire.run.cli

import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.diagnostic.trace
import com.intellij.openapi.project.Project
import com.intellij.platform.eel.provider.getEelDescriptor
import com.intellij.platform.eel.provider.toEelApi
import com.intellij.platform.eel.provider.utils.awaitProcessResult
import com.intellij.platform.eel.provider.utils.stderrString
import com.intellij.platform.eel.provider.utils.stdoutString
import com.intellij.platform.eel.spawnProcess
import com.jetbrains.aspire.extensions.DevCertificateCheckResult
import com.jetbrains.aspire.extensions.DevCertificateProvider
import kotlinx.coroutines.CancellationException

private val LOG = logger<AspireCliDevCertificateProvider>()

/**
 * Rider-free [DevCertificateProvider] that trusts the ASP.NET Core HTTPS development certificate through
 * the Aspire CLI (`aspire certs trust --non-interactive --nologo`).
 *
 * Registered with `order="last"`, so Rider's own provider (registered `order="first"`) keeps precedence
 * when the Rider module is present; this provider is used only in a Rider-free distribution.
 */
internal class AspireCliDevCertificateProvider : DevCertificateProvider {
    override suspend fun checkDevCertificate(useBundledRuntime: Boolean, project: Project): DevCertificateCheckResult {
        val aspireCliPath = AspireCliLocator.locate(project, null) ?: return DevCertificateCheckResult.CheckFailed
        return if (trustDevCertificatesWithAspireCli(project, aspireCliPath)) {
            DevCertificateCheckResult.Trusted
        } else {
            DevCertificateCheckResult.CheckFailed
        }
    }

    // The embedded DCP server runs plain HTTP over loopback, so no certificate export is required.
    override suspend fun exportCertificate(useBundledRuntime: Boolean, project: Project): String? = null
}

/**
 * Runs `aspire certs trust --non-interactive --nologo` via eel and returns whether it succeeded.
 * Shared by [AspireCliDevCertificateProvider] and [AspireCliRunProfileState].
 */
internal suspend fun trustDevCertificatesWithAspireCli(project: Project, aspireCliPath: String): Boolean {
    val eelApi = project.getEelDescriptor().toEelApi()
    return try {
        LOG.trace { "Trusting Aspire dev certificates via $aspireCliPath" }
        val process = eelApi.exec.spawnProcess(aspireCliPath)
            .args("certs", "trust", "--non-interactive", "--nologo")
            .eelIt()
        val result = process.awaitProcessResult()
        if (result.exitCode != 0) {
            LOG.info(
                "aspire certs trust failed with exit code ${result.exitCode}; " +
                        "stdout: ${result.stdoutString}; stderr: ${result.stderrString}"
            )
            false
        } else {
            true
        }
    } catch (ce: CancellationException) {
        throw ce
    } catch (e: Exception) {
        LOG.warn("Unable to trust Aspire dev certificates", e)
        false
    }
}
