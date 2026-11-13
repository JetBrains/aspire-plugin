@file:Suppress("UnstableApiUsage")

package com.jetbrains.aspire.cli

import com.intellij.diagnostic.rethrowControlFlowException
import com.intellij.notification.Notification
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.platform.eel.provider.getEelDescriptor
import com.intellij.platform.eel.provider.toEelApi
import com.jetbrains.aspire.AspireCoreBundle
import com.jetbrains.aspire.worker.AspirePath
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal class AspireCliSelfUpdateServiceImpl(private val project: Project) : AspireCliSelfUpdateService {
    companion object {
        private val LOG = logger<AspireCliSelfUpdateServiceImpl>()
    }

    override suspend fun prepareCommand(): AspireCliCommandLine? = withContext(Dispatchers.IO) {
        try {
            val eelApi = project.getEelDescriptor().toEelApi()
            val cliPath = AspireCliLocator.getInstance(project).locate(refresh = true)
            if (cliPath == null) {
                Notification(
                    "Aspire",
                    AspireCoreBundle.message("notification.aspire.cli.not.installed.title"),
                    AspireCoreBundle.message("notification.aspire.cli.not.installed.content"),
                    NotificationType.WARNING,
                )
                    .addAction(ActionManager.getInstance().getAction("Aspire.InstallCli"))
                    .notify(project)
                return@withContext null
            }

            val command = listOf(cliPath.toString(), "update", "--self")
            AspireCliCommandLine(command, AspirePath(eelApi.userInfo.home.toString()))
        } catch (e: Exception) {
            rethrowControlFlowException(e)
            LOG.warn("Unable to prepare Aspire CLI update", e)
            Notification(
                "Aspire",
                AspireCoreBundle.message("notification.aspire.cli.update.failed.title"),
                AspireCoreBundle.message("notification.aspire.cli.update.failed.content", e.message ?: ""),
                NotificationType.ERROR,
            ).notify(project)
            null
        }
    }
}
