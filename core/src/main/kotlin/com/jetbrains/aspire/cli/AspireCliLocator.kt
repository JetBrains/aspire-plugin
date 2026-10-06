@file:Suppress("UnstableApiUsage")

package com.jetbrains.aspire.cli

import com.intellij.diagnostic.rethrowControlFlowException
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.diagnostic.trace
import com.intellij.openapi.project.Project
import com.intellij.platform.eel.EelApi
import com.intellij.platform.eel.EelDescriptor
import com.intellij.platform.eel.isWindows
import com.intellij.platform.eel.path.EelPath
import com.intellij.platform.eel.provider.getEelDescriptor
import com.intellij.platform.eel.provider.toEelApi
import com.jetbrains.aspire.settings.AspireSettings
import java.util.concurrent.ConcurrentHashMap

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

    private val cachedPaths = ConcurrentHashMap<EelDescriptor, EelPath>()

    /**
     * Returns the configured CLI path, or a cached executable found on the `PATH` or in the default installation directories.
     */
    suspend fun locate(): EelPath? {
        val eelApi = project.getEelDescriptor().toEelApi()

        val configuredPath = AspireSettings.getInstance().aspireCliPath.takeIf { it.isNotBlank() }
        if (configuredPath != null) {
            return EelPath.parse(configuredPath, eelApi.descriptor)
        }

        cachedPaths[eelApi.descriptor]?.let { return it }

        val candidate = resolveFromPath(eelApi) ?: resolveFromDefaultPaths(eelApi)
        if (candidate == null) {
            LOG.trace { "Unable to resolve the aspire CLI executable" }
        } else {
            cachedPaths.putIfAbsent(eelApi.descriptor, candidate)
        }
        return candidate
    }

    private suspend fun resolveFromPath(eelApi: EelApi): EelPath? {
        return try {
            eelApi.exec.findExeFilesInPath(ASPIRE_EXECUTABLE).firstOrNull()
        } catch (e: Exception) {
            rethrowControlFlowException(e)
            LOG.warn("Failed to resolve aspire CLI executable: ${e.message}")
            null
        }
    }

    private suspend fun resolveFromDefaultPaths(eelApi: EelApi): EelPath? {
        return try {
            val executableName = if (eelApi.platform.isWindows) "$ASPIRE_EXECUTABLE.exe" else ASPIRE_EXECUTABLE
            val home = eelApi.userInfo.home
            val defaultPaths = listOf(
                home.resolve(".aspire/bin/$executableName"),
                home.resolve(".dotnet/tools/$executableName")
            )
            defaultPaths.firstNotNullOfOrNull { path ->
                eelApi.exec.findExeFilesInPath(path.toString()).firstOrNull()
            }
        } catch (e: Exception) {
            rethrowControlFlowException(e)
            LOG.warn("Failed to resolve aspire CLI executable from default paths: ${e.message}")
            null
        }
    }
}
