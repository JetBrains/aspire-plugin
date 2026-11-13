package com.jetbrains.aspire.cli

import com.intellij.ide.BrowserUtil
import com.intellij.notification.Notification
import com.intellij.notification.NotificationAction
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.project.Project
import com.jetbrains.aspire.AspireCoreBundle

internal object AspireCliNotifications {
    private const val NOTIFICATION_GROUP = "Aspire"
    private const val INSTALL_URL = "https://aspire.dev/get-started/install-cli/"
    private const val SETTINGS_ACTION_ID = "Aspire.Settings"
    private const val INSTALL_ACTION_ID = "Aspire.InstallCli"

    /**
     * Notifies that the Aspire CLI could not be found and offers to configure its path, read the installation guide,
     * or install it.
     */
    fun notifyCliNotFound(project: Project) {
        val actionManager = ActionManager.getInstance()
        Notification(
            NOTIFICATION_GROUP,
            AspireCoreBundle.message("notification.aspire.cli.not.installed.title"),
            AspireCoreBundle.message("notification.aspire.cli.not.installed.content"),
            NotificationType.WARNING,
        )
            .addAction(actionManager.getAction(SETTINGS_ACTION_ID))
            .addAction(
                NotificationAction.createSimple(AspireCoreBundle.message("notification.aspire.cli.how.to.install.action")) {
                    BrowserUtil.browse(INSTALL_URL)
                }
            )
            .addAction(actionManager.getAction(INSTALL_ACTION_ID))
            .notify(project)
    }
}
