package me.rafaelldi.aspire.actions

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import me.rafaelldi.aspire.AspireBundle

class StopAspireSessionAction(private val projectPath: String) : AnAction(
    AspireBundle.message("action.Aspire.Session.Stop.text"),
    AspireBundle.message("action.Aspire.Session.Stop.description"),
    AllIcons.Actions.Suspend
) {
    override fun actionPerformed(event: AnActionEvent) {
        val project = event.project ?: return

    }

    override fun update(event: AnActionEvent) {
        val project = event.project
        event.presentation.isEnabled = project != null
    }

    override fun getActionUpdateThread() = ActionUpdateThread.BGT
}