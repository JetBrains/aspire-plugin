@file:Suppress("UnstableApiUsage")

package com.jetbrains.aspire.deployment

import com.intellij.openapi.application.EDT
import com.intellij.openapi.components.Service
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
internal class AspireDeploymentTerminalRunner(private val project: Project) {
    suspend fun run(
        operation: AspireDeploymentOperation,
        commandLine: AspireDeploymentCommandLine,
    ) = withContext(Dispatchers.EDT) {
        // The terminal selects its execution environment from the frontend's NIO path.
        val workingDirectory = EelPath.parse(commandLine.workingDirectory.value, project.getEelDescriptor()).asNioPath()
        val tabName = when (operation) {
            AspireDeploymentOperation.Publish ->
                AspireCoreFrontendBundle.message("deployment.terminal.publish.title", commandLine.appHostName)
            AspireDeploymentOperation.Deploy ->
                AspireCoreFrontendBundle.message("deployment.terminal.deploy.title", commandLine.appHostName)
        }
        TerminalToolWindowTabsManager
            .getInstance(project)
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
    }
}
