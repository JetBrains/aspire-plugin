package me.rafaelldi.aspire.actions.notification

import com.intellij.notification.Notification
import com.intellij.notification.NotificationAction
import com.intellij.openapi.actionSystem.AnActionEvent
import me.rafaelldi.aspire.AspireBundle
import me.rafaelldi.aspire.services.AspireWorkloadService

class UpdateWorkloadAction: NotificationAction(AspireBundle.message("notifications.update.aspire.workload")) {
    override fun actionPerformed(e: AnActionEvent, notification: Notification) {
        val project = e.project ?: return
        AspireWorkloadService.getInstance(project).updateWorkload()
    }
}