@file:Suppress("UnstableApiUsage")

package com.jetbrains.aspire.run.cli

import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.diagnostic.trace
import com.intellij.openapi.project.Project
import com.intellij.platform.eel.EelApi
import com.intellij.platform.eel.path.EelPath
import com.intellij.platform.eel.provider.getEelDescriptor
import com.intellij.platform.eel.provider.toEelApi
import com.intellij.platform.eel.provider.utils.awaitProcessResult
import com.intellij.platform.eel.spawnProcess
import kotlinx.coroutines.CancellationException


internal object AspireCliLocator {
    private val LOG = logger<AspireCliLocator>()

    private const val ASPIRE_EXECUTABLE = "aspire"

    /**
     * Return the first `aspire` executable found on the (eel) `PATH`
     */
    suspend fun locate(project: Project): EelPath? {
        val eelApi = project.getEelDescriptor().toEelApi()

        val candidate = resolveCandidate(eelApi)
        if (candidate == null) {
            LOG.trace { "Unable to resolve the aspire CLI executable" }
        }
        return candidate
    }

    private suspend fun resolveCandidate(eelApi: EelApi): EelPath? {
        return try {
            eelApi.exec.findExeFilesInPath(ASPIRE_EXECUTABLE).firstOrNull()
        } catch (ce: CancellationException) {
            throw ce
        } catch (e: Exception) {
            LOG.trace { "Failed to look up aspire CLI on PATH: ${e.message}" }
            null
        }
    }

    suspend fun verifyCliPath(aspireCliPath: EelPath): Boolean {
        return try {
            val process = aspireCliPath.descriptor.toEelApi().exec.spawnProcess(aspireCliPath)
                .args("--version")
                .eelIt()
            process.awaitProcessResult().exitCode == 0
        } catch (ce: CancellationException) {
            throw ce
        } catch (e: Exception) {
            LOG.trace { "Failed to verify aspire CLI ($aspireCliPath): ${e.message}" }
            false
        }
    }
}
