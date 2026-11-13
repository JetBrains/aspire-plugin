@file:Suppress("UnstableApiUsage")

package com.jetbrains.aspire.cli

import com.intellij.diagnostic.rethrowControlFlowException
import com.intellij.notification.Notification
import com.intellij.notification.NotificationType
import com.intellij.openapi.application.EDT
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsContexts
import com.intellij.platform.eel.path.EelPath
import com.intellij.platform.eel.provider.asNioPath
import com.intellij.platform.eel.provider.getEelDescriptor
import com.intellij.terminal.frontend.toolwindow.TerminalToolWindowTabsManager
import com.jetbrains.aspire.AspireCoreFrontendBundle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.plugins.terminal.startup.TerminalProcessType

/**
 * Runs Aspire CLI management commands (installation, self-update) in a new terminal tab.
 */
@Service(Service.Level.PROJECT)
internal class AspireCliTerminalRunner(private val project: Project) {
    companion object {
        private val LOG = logger<AspireCliTerminalRunner>()
    }

    suspend fun run(
        commandLine: AspireCliCommandLine,
        @NlsContexts.TabTitle tabName: String,
    ): Unit = withContext(Dispatchers.EDT) {
        try {
            // The terminal selects its execution environment from the frontend's NIO path.
            val workingDirectory = EelPath.parse(commandLine.workingDirectory.value, project.getEelDescriptor()).asNioPath()
            TerminalToolWindowTabsManager.getInstance(project)
                .createTabBuilder()
                .shellCommand(commandLine.command)
                .workingDirectory(workingDirectory.toString())
                .processType(TerminalProcessType.NON_SHELL)
                .tabName(tabName)
                .requestFocus(true)
                .deferSessionStartUntilUiShown(false)
                .closeOnProcessTermination(false)
                .restoreOnProjectReopen(false)
                .createTab()
        } catch (e: Exception) {
            rethrowControlFlowException(e)
            LOG.warn("Unable to open Aspire CLI terminal", e)
            Notification(
                "Aspire",
                AspireCoreFrontendBundle.message("notification.cli.terminal.failed.title"),
                AspireCoreFrontendBundle.message("notification.cli.terminal.failed.content", tabName, e.message ?: ""),
                NotificationType.ERROR,
            ).notify(project)
        }
    }
}
