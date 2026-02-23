@file:Suppress("UnstableApiUsage")

package com.jetbrains.aspire.actions.dashboard.resource

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
import com.jetbrains.aspire.AspireCoreBundle
import com.jetbrains.aspire.generated.ResourceCommand
import com.jetbrains.aspire.generated.ResourceCommandState
import com.jetbrains.aspire.worker.AspireResource
import com.jetbrains.aspire.dashboard.RestartResourceCommand
import com.jetbrains.aspire.dashboard.StartResourceCommand
import com.jetbrains.aspire.dashboard.StopResourceCommand
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.swing.Icon

class PerformResourceAction : AspireResourceBaseAction() {
    override fun performAction(aspireResource: AspireResource, dataContext: DataContext, project: Project) {
        val commands = getCommands(aspireResource).filter { it.state == ResourceCommandState.Enabled }
        if (commands.isEmpty()) return

        currentThreadCoroutineScope().launch(Dispatchers.EDT) {
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

            popup.showInBestPositionFor(dataContext)

            deferredCommand.await()?.let { command ->
                aspireResource.executeCommand(command.name)
            }
        }
    }

    override fun updateAction(event: AnActionEvent, aspireResource: AspireResource, project: Project) {
        val commands = getCommands(aspireResource)
        if (commands.isEmpty() || !commands.any { it.state == ResourceCommandState.Enabled }) {
            event.presentation.isEnabledAndVisible = false
            return
        }

        event.presentation.isEnabledAndVisible = true
    }

    private fun getCommands(resourceService: AspireResource): List<ResourceCommand> {
        return resourceService.resourceState.value.commands.filter {
            !it.name.equals(StartResourceCommand, true) &&
                    !it.name.equals(StopResourceCommand, true) &&
                    !it.name.equals(RestartResourceCommand, true)
        }
    }
}