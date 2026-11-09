@file:Suppress("UnstableApiUsage")

package com.jetbrains.aspire.cli

import com.intellij.diagnostic.rethrowControlFlowException
import com.intellij.notification.Notification
import com.intellij.notification.NotificationType
import com.intellij.openapi.application.EDT
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.platform.eel.path.EelPath
import com.intellij.platform.eel.provider.asNioPath
import com.intellij.platform.eel.provider.getEelDescriptor
import com.intellij.terminal.frontend.toolwindow.TerminalToolWindowTabsManager
import com.jetbrains.aspire.AspireCoreFrontendBundle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.plugins.terminal.startup.TerminalProcessType

@Service(Service.Level.PROJECT)
internal class AspireCliInstallationTerminalRunner(private val project: Project) {
    companion object {
        private val LOG = logger<AspireCliInstallationTerminalRunner>()
    }

    suspend fun run(commandLine: AspireCliInstallationCommandLine): Unit = withContext(Dispatchers.EDT) {
        try {
            val workingDirectory = EelPath.parse(commandLine.workingDirectory.value, project.getEelDescriptor()).asNioPath()
            TerminalToolWindowTabsManager.getInstance(project)
                .createTabBuilder()
                .shellCommand(commandLine.command)
                .workingDirectory(workingDirectory.toString())
                .processType(TerminalProcessType.NON_SHELL)
                .tabName(AspireCoreFrontendBundle.message("cli.install.terminal.title"))
                .requestFocus(true)
                .deferSessionStartUntilUiShown(false)
                .closeOnProcessTermination(false)
                .restoreOnProjectReopen(false)
                .createTab()
        } catch (e: Exception) {
            rethrowControlFlowException(e)
            LOG.warn("Unable to open Aspire CLI installation terminal", e)
            Notification(
                "Aspire",
                AspireCoreFrontendBundle.message("notification.cli.install.terminal.failed.title"),
                AspireCoreFrontendBundle.message("notification.cli.install.terminal.failed.content", e.message ?: ""),
                NotificationType.ERROR,
            ).notify(project)
        }
    }
}
