package com.jetbrains.aspire.run.cli

import com.intellij.ide.BrowserUtil
import com.intellij.notification.Notification
import com.intellij.notification.NotificationAction
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.Project
import com.jetbrains.aspire.AspireCoreBundle

internal object AspireCliNotifications {
    private const val NOTIFICATION_GROUP = "Aspire"
    private const val INSTALL_URL = "https://learn.microsoft.com/en-us/dotnet/aspire/cli/install"

    fun notifyCliNotInstalled(project: Project) {
        Notification(
            NOTIFICATION_GROUP,
            AspireCoreBundle.message("notification.aspire.cli.not.installed.title"),
            AspireCoreBundle.message("notification.aspire.cli.not.installed.content"),
            NotificationType.WARNING
        )
            .addAction(object :
                NotificationAction(AspireCoreBundle.message("notification.aspire.cli.install.action")) {
                override fun actionPerformed(e: AnActionEvent, notification: Notification) {
                    BrowserUtil.browse(INSTALL_URL)
                }
            })
            .notify(project)
    }
}
