@file:Suppress("UnstableApiUsage")

package com.jetbrains.rider.aspire.actions.dashboard.resource

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.application.EDT
import com.intellij.openapi.progress.currentThreadCoroutineScope
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.ui.popup.JBPopupListener
import com.intellij.openapi.ui.popup.LightweightWindowEvent
import com.intellij.openapi.ui.popup.PopupStep
import com.intellij.openapi.ui.popup.util.BaseListPopupStep
import com.jetbrains.rider.aspire.AspireBundle
import com.jetbrains.rider.aspire.generated.ResourceCommand
import com.jetbrains.rider.aspire.generated.ResourceCommandState
import com.jetbrains.rider.aspire.dashboard.AspireResource
import com.jetbrains.rider.aspire.dashboard.RestartResourceCommand
import com.jetbrains.rider.aspire.dashboard.StartResourceCommand
import com.jetbrains.rider.aspire.dashboard.StopResourceCommand
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.swing.Icon

class PerformResourceAction : AspireResourceBaseAction() {
    override fun performAction(resourceService: AspireResource, dataContext: DataContext, project: Project) {
        val commands = getCommands(resourceService).filter { it.state == ResourceCommandState.Enabled }
        if (commands.isEmpty()) return

        currentThreadCoroutineScope().launch(Dispatchers.EDT) {
            val deferredCommand = CompletableDeferred<ResourceCommand?>()

            val popup = JBPopupFactory.getInstance().createListPopup(object :
                BaseListPopupStep<ResourceCommand>(
                    AspireBundle.message("resource.choose.command.popup.header"),
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

            popup.showInBestPositionFor(dataContext)

            deferredCommand.await()?.let { command ->
                resourceService.executeCommand(command.name)
            }
        }
    }

    override fun updateAction(event: AnActionEvent, resourceService: AspireResource, project: Project) {
        val commands = getCommands(resourceService)
        if (commands.isEmpty() || !commands.any { it.state == ResourceCommandState.Enabled }) {
            event.presentation.isEnabledAndVisible = false
            return
        }

        event.presentation.isEnabledAndVisible = true
    }

    private fun getCommands(resourceService: AspireResource): List<ResourceCommand> {
        return resourceService.commands.filter {
            !it.name.equals(StartResourceCommand, true) &&
                    !it.name.equals(StopResourceCommand, true) &&
                    !it.name.equals(RestartResourceCommand, true)
        }
    }
}