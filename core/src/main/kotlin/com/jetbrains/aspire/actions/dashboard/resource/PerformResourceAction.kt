@file:Suppress("UnstableApiUsage")

package com.jetbrains.aspire.actions.dashboard.resource

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.EDT
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.ui.popup.JBPopupListener
import com.intellij.openapi.ui.popup.LightweightWindowEvent
import com.intellij.openapi.ui.popup.PopupStep
import com.intellij.openapi.ui.popup.util.BaseListPopupStep
import com.jetbrains.aspire.AspireCoreBundle
import com.jetbrains.aspire.worker.AspireResourceCommandExecutor
import com.jetbrains.aspire.worker.getNonDefaultCommands
import com.jetbrains.aspire.worker.ResourceCommand
import com.jetbrains.aspire.worker.ResourceCommandState
import com.jetbrains.aspire.worker.AspireResourceData
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.swing.Icon

internal class PerformResourceAction : AspireResourceBaseAction() {
    override fun performAction(event: AnActionEvent, resourceData: AspireResourceData, project: Project) {
        val commands = getCommands(resourceData).filter { it.state == ResourceCommandState.Enabled }
        if (commands.isEmpty()) return

        event.coroutineScope.launch(Dispatchers.EDT) {
            val deferredCommand = CompletableDeferred<ResourceCommand?>()

            val popup = JBPopupFactory.getInstance().createListPopup(object :
                BaseListPopupStep<ResourceCommand>(
                    AspireCoreBundle.message("resource.choose.command.popup.header"),
                    commands
                ) {

                override fun getTextFor(command: ResourceCommand) = command.displayName

                override fun getIconFor(command: ResourceCommand): Icon? {
                    if (command.iconName.equals("bug", true)) return AllIcons.Actions.StartDebugger
                    return null
                }

                override fun onChosen(selectedValue: ResourceCommand, finalChoice: Boolean): PopupStep<*>? {
                    return doFinalStep { deferredCommand.complete(selectedValue) }
                }
            }).apply {
                addListener(object : JBPopupListener {
                    override fun onClosed(event: LightweightWindowEvent) {
                        if (!event.isOk) deferredCommand.complete(null)
                    }
                })
            }

            popup.showInBestPositionFor(event.dataContext)

            deferredCommand.await()?.let { command ->
                project.service<AspireResourceCommandExecutor>().executeCommand(resourceData.id, command.name)
            }
        }
    }

    override fun updateAction(event: AnActionEvent, resourceData: AspireResourceData, project: Project) {
        val commands = getCommands(resourceData)
        if (commands.isEmpty() || !commands.any { it.state == ResourceCommandState.Enabled }) {
            event.presentation.isEnabledAndVisible = false
            return
        }

        event.presentation.isEnabledAndVisible = true
    }

    private fun getCommands(resourceData: AspireResourceData): List<ResourceCommand> {
        return resourceData.commands.getNonDefaultCommands()
    }
}
