@file:Suppress("UnstableApiUsage")

package com.jetbrains.aspire.cli

import com.intellij.diagnostic.rethrowControlFlowException
import com.intellij.execution.ExecutionException
import com.intellij.notification.Notification
import com.intellij.notification.NotificationType
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.platform.eel.EelPlatform
import com.intellij.platform.eel.isWindows
import com.intellij.platform.eel.path.EelPath
import com.intellij.platform.eel.path.EelPathException
import com.intellij.platform.eel.provider.getEelDescriptor
import com.intellij.platform.eel.provider.toEelApi
import com.jetbrains.aspire.AspireCoreBundle
import com.jetbrains.aspire.settings.AspireSettings
import com.jetbrains.aspire.worker.AspirePath
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal class AspireCliInstallationServiceImpl(private val project: Project) : AspireCliInstallationService {
    companion object {
        private val LOG = logger<AspireCliInstallationServiceImpl>()
    }

    override suspend fun prepareCommand(): AspireCliCommandLine? = withContext(Dispatchers.IO) {
        try {
            val eelApi = project.getEelDescriptor().toEelApi()
            val configuredPath = AspireSettings.getInstance().aspireCliPath
            if (configuredPath.isNotBlank()) {
                try {
                    EelPath.parse(configuredPath, eelApi.descriptor)
                } catch (e: EelPathException) {
                    throw ExecutionException(
                        AspireCoreBundle.message("notification.aspire.cli.install.invalid.path", configuredPath), e,
                    )
                }
            }

            val locatedCli =
                if (configuredPath.isBlank()) AspireCliLocator.getInstance(project).locate(refresh = true)
                else null

            prepareCommand(
                eelApi.platform,
                AspirePath(eelApi.userInfo.home.toString()),
                configuredPath,
                locatedCli?.let { AspirePath(it.toString()) },
            ) { name ->
                eelApi.exec.findExeFilesInPath(name).firstOrNull()?.let { AspirePath(it.toString()) }
            }
        } catch (e: Exception) {
            rethrowControlFlowException(e)
            LOG.warn("Unable to prepare Aspire CLI installation", e)
            Notification(
                "Aspire",
                AspireCoreBundle.message("notification.aspire.cli.install.failed.title"),
                AspireCoreBundle.message("notification.aspire.cli.install.failed.content", e.message ?: ""),
                NotificationType.ERROR,
            ).notify(project)
            null
        }
    }

    private suspend fun prepareCommand(
        platform: EelPlatform,
        homeDirectory: AspirePath,
        configuredPath: String,
        locatedCli: AspirePath?,
        findExecutable: suspend (String) -> AspirePath?,
    ): AspireCliCommandLine? {
        val existingCli = if (configuredPath.isNotBlank()) {
            findExecutable(configuredPath) ?: throw ExecutionException(
                AspireCoreBundle.message("notification.aspire.cli.install.invalid.path", configuredPath),
            )
        } else {
            locatedCli?.let { findExecutable(it.value) }
        }

        if (existingCli != null) {
            Notification(
                "Aspire",
                AspireCoreBundle.message("notification.aspire.cli.already.installed.title"),
                AspireCoreBundle.message("notification.aspire.cli.already.installed.content", existingCli.value),
                NotificationType.INFORMATION,
            ).notify(project)
            return null
        }

        //see: https://aspire.dev/get-started/install-cli/#install-with-the-install-script
        val command = if (platform.isWindows) {
            val powershell = findExecutable("pwsh")
                ?: findExecutable("powershell.exe")
                ?: throw missingTool("PowerShell")
            listOf(
                powershell.value, "-NoProfile", "-ExecutionPolicy", "Bypass", "-Command",
                $$"$ErrorActionPreference = 'Stop'; Invoke-RestMethod -Uri 'https://aspire.dev/install.ps1' | Invoke-Expression",
            )
        } else {
            val bash = findExecutable("bash") ?: throw missingTool("Bash")
            val curl = findExecutable("curl") ?: throw missingTool("curl")
            val script = "${shellQuote(curl.value)} -fsSL https://aspire.dev/install.sh | ${shellQuote(bash.value)}"
            listOf(bash.value, "-o", "pipefail", "-c", script)
        }

        return AspireCliCommandLine(command, homeDirectory)
    }

    private fun missingTool(name: String) =
        ExecutionException(AspireCoreBundle.message("notification.aspire.cli.install.missing.tool", name))

    private fun shellQuote(value: String) =
        "'${value.replace("'", "'\\''")}'"
}
