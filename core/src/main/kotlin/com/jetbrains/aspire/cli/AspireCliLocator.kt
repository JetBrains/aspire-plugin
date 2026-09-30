@file:Suppress("UnstableApiUsage")

package com.jetbrains.aspire.cli

import com.intellij.diagnostic.rethrowControlFlowException
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.diagnostic.trace
import com.intellij.openapi.project.Project
import com.intellij.platform.eel.EelApi
import com.intellij.platform.eel.path.EelPath
import com.intellij.platform.eel.provider.getEelDescriptor
import com.intellij.platform.eel.provider.toEelApi
import com.jetbrains.aspire.settings.AspireSettings

/**
 * Locates the `aspire` CLI executable and checks that it is operable.
 */
@Service(Service.Level.PROJECT)
internal class AspireCliLocator(private val project: Project) {
    companion object {
        fun getInstance(project: Project): AspireCliLocator = project.service()
        private val LOG = logger<AspireCliLocator>()

        private const val ASPIRE_EXECUTABLE = "aspire"
    }

    /**
     * Returns the configured CLI path, or the first `aspire` executable found on the `PATH`.
     */
    suspend fun locate(): EelPath? {
        val eelApi = project.getEelDescriptor().toEelApi()

        val configuredPath = AspireSettings.getInstance().aspireCliPath.takeIf { it.isNotBlank() }
        if (configuredPath != null) {
            return EelPath.parse(configuredPath, eelApi.descriptor)
        }

        val candidate = resolveCandidate(eelApi)
        if (candidate == null) {
            LOG.trace { "Unable to resolve the aspire CLI executable" }
        }
        return candidate
    }

    private suspend fun resolveCandidate(eelApi: EelApi): EelPath? {
        return try {
            eelApi.exec.findExeFilesInPath(ASPIRE_EXECUTABLE).firstOrNull()
        } catch (e: Exception) {
            rethrowControlFlowException(e)
            LOG.warn("Failed to resolve aspire CLI executable: ${e.message}")
            null
        }
    }
}
