@file:Suppress("UnstableApiUsage")

package com.jetbrains.aspire.cli

import com.intellij.diagnostic.rethrowControlFlowException
import com.intellij.execution.ExecutionException
import com.intellij.notification.Notification
import com.intellij.notification.NotificationType
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.platform.eel.EelApi
import com.intellij.platform.eel.isWindows
import com.intellij.platform.eel.provider.getEelDescriptor
import com.intellij.platform.eel.provider.toEelApi
import com.jetbrains.aspire.AspireCoreBundle
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
            val cliPath = AspireCliLocator.getInstance(project).locate(refresh = true)
            if (cliPath != null) {
                Notification(
                    "Aspire",
                    AspireCoreBundle.message("notification.aspire.cli.already.installed.title"),
                    AspireCoreBundle.message("notification.aspire.cli.already.installed.content", cliPath.toString()),
                    NotificationType.INFORMATION,
                ).notify(project)
                return@withContext null
            }

            prepareCommand(eelApi)
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

    private suspend fun prepareCommand(eelApi: EelApi): AspireCliCommandLine {
        //see: https://aspire.dev/get-started/install-cli/#install-with-the-install-script
        val command = if (eelApi.platform.isWindows) {
            val powershell = findExecutable(eelApi, "pwsh")
                ?: findExecutable(eelApi, "powershell.exe")
                ?: throw missingTool("PowerShell")
            listOf(
                powershell, "-NoProfile", "-ExecutionPolicy", "Bypass", "-Command",
                $$"$ErrorActionPreference = 'Stop'; Invoke-RestMethod -Uri 'https://aspire.dev/install.ps1' | Invoke-Expression",
            )
        } else {
            val bash = findExecutable(eelApi, "bash") ?: throw missingTool("Bash")
            val curl = findExecutable(eelApi, "curl") ?: throw missingTool("curl")
            val script = "${shellQuote(curl)} -fsSL https://aspire.dev/install.sh | ${shellQuote(bash)}"
            listOf(bash, "-o", "pipefail", "-c", script)
        }

        val workingDirectory = AspirePath(eelApi.userInfo.home.toString())
        return AspireCliCommandLine(command, workingDirectory)
    }

    private suspend fun findExecutable(eelApi: EelApi, name: String) =
        eelApi.exec.findExeFilesInPath(name).firstOrNull()?.toString()

    private fun missingTool(name: String) =
        ExecutionException(AspireCoreBundle.message("notification.aspire.cli.install.missing.tool", name))

    private fun shellQuote(value: String) =
        "'${value.replace("'", "'\\''")}'"
}
