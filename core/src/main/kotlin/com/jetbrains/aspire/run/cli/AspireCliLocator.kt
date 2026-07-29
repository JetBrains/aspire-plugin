@file:Suppress("UnstableApiUsage")

package com.jetbrains.aspire.run.cli

import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.diagnostic.trace
import com.intellij.openapi.project.Project
import com.intellij.platform.eel.EelApi
import com.intellij.platform.eel.provider.getEelDescriptor
import com.intellij.platform.eel.provider.toEelApi
import com.intellij.platform.eel.provider.utils.awaitProcessResult
import com.intellij.platform.eel.spawnProcess
import kotlinx.coroutines.CancellationException
import kotlin.io.path.Path
import kotlin.io.path.exists

/**
 * Resolves the `aspire` CLI executable.
 *
 * Resolution order:
 * 1. an explicit override path (from the run configuration), if it exists;
 * 2. the first `aspire` executable found on the (eel) `PATH`.
 *
 * The resolved candidate is validated with `aspire --version` before it is returned.
 */
internal object AspireCliLocator {
    private val LOG = logger<AspireCliLocator>()

    private const val ASPIRE_EXECUTABLE = "aspire"

    suspend fun locate(project: Project, overridePath: String?): String? {
        val eelApi = project.getEelDescriptor().toEelApi()

        val candidate = resolveCandidate(eelApi, overridePath)
        if (candidate == null) {
            LOG.trace { "Unable to resolve the aspire CLI executable" }
            return null
        }

        return if (verify(eelApi, candidate)) {
            LOG.trace { "Resolved aspire CLI executable: $candidate" }
            candidate
        } else {
            LOG.trace { "Candidate aspire CLI executable did not pass verification: $candidate" }
            null
        }
    }

    private suspend fun resolveCandidate(eelApi: EelApi, overridePath: String?): String? {
        if (!overridePath.isNullOrBlank()) {
            return if (Path(overridePath).exists()) overridePath else null
        }

        return try {
            eelApi.exec.findExeFilesInPath(ASPIRE_EXECUTABLE).firstOrNull()?.toString()
        } catch (ce: CancellationException) {
            throw ce
        } catch (e: Exception) {
            LOG.trace { "Failed to look up aspire CLI on PATH: ${e.message}" }
            null
        }
    }

    private suspend fun verify(eelApi: EelApi, executablePath: String): Boolean {
        return try {
            val process = eelApi.exec.spawnProcess(executablePath)
                .args("--version")
                .eelIt()
            process.awaitProcessResult().exitCode == 0
        } catch (ce: CancellationException) {
            throw ce
        } catch (e: Exception) {
            LOG.trace { "Failed to verify aspire CLI ($executablePath): ${e.message}" }
            false
        }
    }
}
