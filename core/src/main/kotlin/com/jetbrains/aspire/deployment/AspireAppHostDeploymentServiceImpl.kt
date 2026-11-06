@file:Suppress("UnstableApiUsage")

package com.jetbrains.aspire.deployment

import com.intellij.diagnostic.rethrowControlFlowException
import com.intellij.ide.BrowserUtil
import com.intellij.notification.Notification
import com.intellij.notification.NotificationAction
import com.intellij.notification.NotificationType
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.platform.eel.provider.asEelPath
import com.jetbrains.aspire.AspireCoreBundle
import com.jetbrains.aspire.cli.AspireCliLocator
import com.jetbrains.aspire.worker.AspireAppHostId
import com.jetbrains.aspire.worker.AspirePath
import com.jetbrains.aspire.worker.AspireWorker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.io.path.isRegularFile

internal class AspireAppHostDeploymentServiceImpl(private val project: Project) : AspireAppHostDeploymentService {
    companion object {
        private val LOG = logger<AspireAppHostDeploymentServiceImpl>()
    }

    override suspend fun prepareCommand(
        appHostId: AspireAppHostId,
        operation: AspireDeploymentOperation,
    ): AspireDeploymentCommandLine? = withContext(Dispatchers.IO) {
        try {
            val appHost = AspireWorker.getInstance(project).getAppHostById(appHostId)
            if (appHost == null || !appHost.appHostPath.isRegularFile()) {
                Notification(
                    "Aspire",
                    AspireCoreBundle.message("notification.aspire.deployment.app.host.not.found.title"),
                    AspireCoreBundle.message("notification.aspire.deployment.app.host.not.found.content"),
                    NotificationType.WARNING,
                ).notify(project)
                return@withContext null
            }

            val appHostPath = appHost.appHostPath.toAbsolutePath()
            val appHostEelPath = appHostPath.asEelPath()
            val cliPath = AspireCliLocator.getInstance(project).locate()
            if (cliPath == null) {
                Notification(
                    "Aspire",
                    AspireCoreBundle.message("notification.aspire.cli.not.installed.title"),
                    AspireCoreBundle.message("notification.aspire.cli.not.installed.content"),
                    NotificationType.WARNING,
                ).addAction(
                    NotificationAction.createSimple(AspireCoreBundle.message("notification.aspire.cli.install.action")) {
                        BrowserUtil.browse("https://aspire.dev/get-started/install-cli/")
                    }
                ).notify(project)
                return@withContext null
            }

            val command = listOf(cliPath.toString(), operation.commandName, "--apphost", appHostEelPath.toString())
            val workingDirectory = AspirePath(appHostPath.parent.asEelPath().toString())
            AspireDeploymentCommandLine(appHost.name, command, workingDirectory)
        } catch (e: Exception) {
            rethrowControlFlowException(e)
            LOG.warn("Unable to prepare Aspire deployment command", e)
            Notification(
                "Aspire",
                AspireCoreBundle.message("notification.aspire.deployment.prepare.failed.title"),
                AspireCoreBundle.message("notification.aspire.deployment.prepare.failed.content", e.message ?: ""),
                NotificationType.ERROR,
            ).notify(project)
            null
        }
    }
}
