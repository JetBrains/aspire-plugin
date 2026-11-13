@file:Suppress("UnstableApiUsage")

package com.jetbrains.aspire.cli

import com.intellij.diagnostic.rethrowControlFlowException
import com.intellij.execution.ExecutionException
import com.intellij.notification.Notification
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.text.StringUtil
import com.intellij.platform.eel.EelApi
import com.intellij.platform.eel.isWindows
import com.intellij.platform.eel.path.EelPath
import com.intellij.platform.eel.provider.asEelPath
import com.intellij.platform.eel.provider.getEelDescriptor
import com.intellij.platform.eel.provider.toEelApi
import com.jetbrains.aspire.AspireCoreBundle
import com.jetbrains.aspire.cli.AspireCliCommand.AppHostCommand
import com.jetbrains.aspire.worker.AspirePath
import com.jetbrains.aspire.worker.AspireWorker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.io.path.isRegularFile

internal class AspireCliCommandServiceImpl(private val project: Project) : AspireCliCommandService {
    companion object {
        private val LOG = logger<AspireCliCommandServiceImpl>()
    }

    override suspend fun prepare(command: AspireCliCommand): AspireCliCommandLine? = withContext(Dispatchers.IO) {
        try {
            when (command) {
                AspireCliCommand.Install -> prepareInstall()
                AspireCliCommand.SelfUpdate -> prepareSelfUpdate()
                is AppHostCommand -> prepareAppHostCommand(command)
            }
        } catch (e: AspireCliInvalidPathException) {
            LOG.info("Unable to prepare Aspire command $command: ${e.message}")
            notifyInvalidCliPath(e)
            null
        } catch (e: Exception) {
            rethrowControlFlowException(e)
            LOG.warn("Unable to prepare Aspire command $command", e)
            notifyPreparationFailed(command, e)
            null
        }
    }

    private suspend fun prepareInstall(): AspireCliCommandLine? {
        val cliPath = AspireCliLocator.getInstance(project).locate(refresh = true)
        if (cliPath != null) {
            Notification(
                "Aspire",
                AspireCoreBundle.message("notification.aspire.cli.already.installed.title"),
                AspireCoreBundle.message("notification.aspire.cli.already.installed.content", cliPath.toString()),
                NotificationType.INFORMATION,
            ).notify(project)
            return null
        }

        val eelApi = project.getEelDescriptor().toEelApi()
        return AspireCliCommandLine(buildInstallCommand(eelApi), homeDirectory(eelApi))
    }

    private suspend fun prepareSelfUpdate(): AspireCliCommandLine? {
        val cliPath = locateCliOrNotify(refresh = true) ?: return null
        val eelApi = project.getEelDescriptor().toEelApi()
        return AspireCliCommandLine(listOf(cliPath.toString(), "update", "--self"), homeDirectory(eelApi))
    }

    private suspend fun prepareAppHostCommand(command: AppHostCommand): AspireCliCommandLine? {
        val appHost = AspireWorker.getInstance(project).getAppHostById(command.appHostId)
        if (appHost == null || !appHost.appHostPath.isRegularFile()) {
            Notification(
                "Aspire",
                AspireCoreBundle.message("notification.aspire.deployment.app.host.not.found.title"),
                AspireCoreBundle.message("notification.aspire.deployment.app.host.not.found.content"),
                NotificationType.WARNING,
            ).notify(project)
            return null
        }

        val cliPath = locateCliOrNotify(refresh = false) ?: return null
        val appHostPath = appHost.appHostPath.toAbsolutePath()
        val arguments = command.arguments(appHostPath.asEelPath())
        val workingDirectory = AspirePath(appHostPath.parent.asEelPath().toString())
        return AspireCliCommandLine(listOf(cliPath.toString()) + arguments, workingDirectory)
    }

    private fun AppHostCommand.arguments(appHostPath: EelPath): List<String> {
        val appHostArguments = listOf("--apphost", appHostPath.toString())
        return when (this) {
            is AspireCliCommand.Publish -> listOf("publish") + appHostArguments
            is AspireCliCommand.Deploy -> listOf("deploy") + appHostArguments
        }
    }

    private suspend fun locateCliOrNotify(refresh: Boolean): EelPath? {
        val cliPath = AspireCliLocator.getInstance(project).locate(refresh)
        if (cliPath == null) {
            AspireCliNotifications.notifyCliNotFound(project)
        }
        return cliPath
    }

    private fun homeDirectory(eelApi: EelApi) = AspirePath(eelApi.userInfo.home.toString())

    private suspend fun buildInstallCommand(eelApi: EelApi): List<String> {
        //see: https://aspire.dev/get-started/install-cli/#install-with-the-install-script
        return if (eelApi.platform.isWindows) {
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
    }

    private suspend fun findExecutable(eelApi: EelApi, name: String) =
        eelApi.exec.findExeFilesInPath(name).firstOrNull()?.toString()

    private fun missingTool(name: String) =
        ExecutionException(AspireCoreBundle.message("notification.aspire.cli.install.missing.tool", name))

    private fun shellQuote(value: String) =
        "'${value.replace("'", "'\\''")}'"

    private fun notifyInvalidCliPath(e: AspireCliInvalidPathException) {
        Notification(
            "Aspire",
            AspireCoreBundle.message("notification.aspire.cli.invalid.path.title"),
            AspireCoreBundle.message("aspire.cli.invalid.path", StringUtil.escapeXmlEntities(e.configuredPath)),
            NotificationType.WARNING,
        )
            .addAction(ActionManager.getInstance().getAction("Aspire.Settings"))
            .notify(project)
    }

    private fun notifyPreparationFailed(command: AspireCliCommand, e: Exception) {
        val (titleKey, contentKey) = when (command) {
            AspireCliCommand.Install ->
                "notification.aspire.cli.install.failed.title" to "notification.aspire.cli.install.failed.content"
            AspireCliCommand.SelfUpdate ->
                "notification.aspire.cli.update.failed.title" to "notification.aspire.cli.update.failed.content"
            is AppHostCommand ->
                "notification.aspire.deployment.prepare.failed.title" to "notification.aspire.deployment.prepare.failed.content"
        }
        Notification(
            "Aspire",
            AspireCoreBundle.message(titleKey),
            AspireCoreBundle.message(contentKey, e.message ?: ""),
            NotificationType.ERROR,
        ).notify(project)
    }
}
