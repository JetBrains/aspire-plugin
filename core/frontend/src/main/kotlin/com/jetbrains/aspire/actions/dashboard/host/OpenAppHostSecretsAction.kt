@file:Suppress("UnstableApiUsage")

package com.jetbrains.aspire.actions.dashboard.host

import com.intellij.notification.Notification
import com.intellij.notification.NotificationAction
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.EDT
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.ui.popup.JBPopupListener
import com.intellij.openapi.ui.popup.LightweightWindowEvent
import com.intellij.openapi.ui.popup.PopupStep
import com.intellij.openapi.ui.popup.util.BaseListPopupStep
import com.intellij.platform.ide.progress.withBackgroundProgress
import com.intellij.ui.awt.RelativePoint
import com.jetbrains.aspire.AspireCoreFrontendBundle
import com.jetbrains.aspire.secrets.AspireSecretCreator
import com.jetbrains.aspire.secrets.AspireSecretsEnvironments
import com.jetbrains.aspire.secrets.AspireSecretsService
import com.jetbrains.aspire.secrets.AspireSecretsStore
import com.jetbrains.aspire.worker.AspireAppHostData
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Opens the Aspire secrets file of the AppHost for an environment that the user chooses.
 *
 * If the AppHost has no secrets files, the action tells the user so.
 *
 * See: https://aspire.dev/reference/cli/commands/aspire-secret-path/
 */
internal class OpenAppHostSecretsAction : AspireAppHostBaseAction() {
    override fun performAction(event: AnActionEvent, appHostData: AspireAppHostData, project: Project) {
        val popupLocation = JBPopupFactory.getInstance().guessBestPopupLocation(this, event)
        event.coroutineScope.launch {
            val store = getSecretsStore(project, appHostData) ?: return@launch
            if (store.environments.isEmpty()) {
                notifyNoSecrets(project, appHostData)
                return@launch
            }

            val environment = chooseEnvironment(store, popupLocation) ?: return@launch
            project.service<AspireSecretsService>().openSecretsFile(store, environment)
        }
    }

    override fun updateAction(event: AnActionEvent, appHostData: AspireAppHostData) {
        event.presentation.isEnabledAndVisible = true
    }

    private suspend fun getSecretsStore(project: Project, appHostData: AspireAppHostData): AspireSecretsStore? =
        withBackgroundProgress(project, AspireCoreFrontendBundle.message("secrets.progress.title", appHostData.name)) {
            project.service<AspireSecretsService>().getSecretsStore(appHostData.id)
        }

    private fun notifyNoSecrets(project: Project, appHostData: AspireAppHostData) {
        Notification(
            "Aspire",
            AspireCoreFrontendBundle.message("notification.secrets.not.found.title"),
            AspireCoreFrontendBundle.message("notification.secrets.not.found.content", appHostData.name),
            NotificationType.INFORMATION,
        )
            .addAction(
                NotificationAction.createSimpleExpiring(
                    AspireCoreFrontendBundle.message("notification.secrets.not.found.add.action")
                ) {
                    AspireSecretCreator.getInstance(project).createSecret(appHostData)
                }
            )
            .notify(project)
    }

    private suspend fun chooseEnvironment(store: AspireSecretsStore, popupLocation: RelativePoint): String? =
        withContext(Dispatchers.EDT) {
            val chosenEnvironment = CompletableDeferred<String?>()

            val popup = JBPopupFactory.getInstance().createListPopup(object : BaseListPopupStep<String>(
                AspireCoreFrontendBundle.message("secrets.environment.popup.title"),
                AspireSecretsEnvironments.sort(store.environments),
            ) {
                override fun isSpeedSearchEnabled() = true

                override fun onChosen(selectedValue: String, finalChoice: Boolean): PopupStep<*>? =
                    doFinalStep { chosenEnvironment.complete(selectedValue) }
            }).apply {
                addListener(object : JBPopupListener {
                    override fun onClosed(event: LightweightWindowEvent) {
                        if (!event.isOk) chosenEnvironment.complete(null)
                    }
                })
            }
            popup.show(popupLocation)

            try {
                chosenEnvironment.await()
            } catch (e: CancellationException) {
                popup.cancel()
                throw e
            }
        }
}
