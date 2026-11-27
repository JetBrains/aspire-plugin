@file:Suppress("UnstableApiUsage")

package com.jetbrains.aspire.cli

import com.intellij.diagnostic.rethrowControlFlowException
import com.intellij.notification.Notification
import com.intellij.notification.NotificationType
import com.intellij.openapi.application.EDT
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsContexts
import com.intellij.platform.eel.path.EelPath
import com.intellij.platform.eel.provider.asNioPath
import com.intellij.platform.eel.provider.getEelDescriptor
import com.intellij.terminal.frontend.toolwindow.TerminalToolWindowTab
import com.intellij.terminal.frontend.toolwindow.TerminalToolWindowTabsManager
import com.intellij.terminal.frontend.view.TerminalViewSessionState
import com.jetbrains.aspire.AspireCoreFrontendBundle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.plugins.terminal.startup.TerminalProcessType

/**
 * Prepares an [AspireCliCommand] and runs it in a new terminal tab.
 */
@Service(Service.Level.PROJECT)
internal class AspireCliCommandRunner(private val project: Project) {
    companion object {
        fun getInstance(project: Project): AspireCliCommandRunner = project.service()

        private val LOG = logger<AspireCliCommandRunner>()
    }

    /**
     * Runs [command] in a new terminal tab.
     *
     * If [onTerminated] is set, it runs in the scope of the tab after the process of the command terminates.
     * It doesn't run if the command didn't start or if the user closes the tab before the process terminates,
     * so nothing waits for the command after its tab is gone.
     *
     * If [startWhenTabShown] is set, the process starts only when the tab is shown, so it gets the actual terminal size.
     * Use it for full-screen commands that read the size only at startup. Otherwise, the process starts immediately
     * with the default size, so the command also runs when the tab is never shown (e.g., in headless tests).
     */
    suspend fun run(
        command: AspireCliCommand,
        @NlsContexts.TabTitle tabName: String,
        startWhenTabShown: Boolean = false,
        onTerminated: (suspend () -> Unit)? = null,
    ) {
        val tab = openTerminal(command, tabName, startWhenTabShown) ?: return
        if (onTerminated == null) return

        val view = tab.view
        view.coroutineScope.launch {
            view.sessionState.first { it == TerminalViewSessionState.Terminated }
            onTerminated()
        }
    }

    private suspend fun openTerminal(
        command: AspireCliCommand,
        @NlsContexts.TabTitle tabName: String,
        startWhenTabShown: Boolean,
    ): TerminalToolWindowTab? {
        val commandLine = project.service<AspireCliCommandService>().prepare(command) ?: return null
        return openTerminal(commandLine, tabName, startWhenTabShown)
    }

    private suspend fun openTerminal(
        commandLine: AspireCliCommandLine,
        @NlsContexts.TabTitle tabName: String,
        startWhenTabShown: Boolean,
    ): TerminalToolWindowTab? = withContext(Dispatchers.EDT) {
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
                .deferSessionStartUntilUiShown(startWhenTabShown)
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
            null
        }
    }
}
