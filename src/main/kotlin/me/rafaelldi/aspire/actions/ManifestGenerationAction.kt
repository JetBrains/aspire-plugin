package me.rafaelldi.aspire.actions

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.platform.backend.workspace.virtualFile
import com.jetbrains.rider.model.RdProjectDescriptor
import com.jetbrains.rider.projectView.nodes.getUserData
import com.jetbrains.rider.projectView.workspace.getProjectModelEntity
import com.jetbrains.rider.runtime.RiderDotNetActiveRuntimeHost
import me.rafaelldi.aspire.manifest.ManifestService

class ManifestGenerationAction : AnAction() {
    override fun actionPerformed(event: AnActionEvent) {
        val project = event.project ?: return
        val entity = event.dataContext.getProjectModelEntity() ?: return
        val descriptor = entity.descriptor as? RdProjectDescriptor ?: return
        if (!descriptor.isDotNetCore) return
        val isAspireHost = descriptor.getUserData("IsAspireHost")
        if (isAspireHost?.equals("true", true) != true) return
        val file = entity.url?.virtualFile ?: return

        ManifestService.getInstance(project).generateManifest(file.toNioPath())
    }

    override fun update(event: AnActionEvent) {
        val project = event.project
        if (project == null) {
            event.presentation.isEnabledAndVisible = false
            return
        }

        val runtime = RiderDotNetActiveRuntimeHost.getInstance(project).dotNetCoreRuntime.value
        if (runtime == null) {
            event.presentation.isEnabledAndVisible = false
            return
        }

        val entity = event.dataContext.getProjectModelEntity()
        val descriptor = entity?.descriptor
        if (descriptor == null || descriptor !is RdProjectDescriptor || !descriptor.isDotNetCore) {
            event.presentation.isEnabledAndVisible = false
            return
        }

        val isAspireHost = descriptor.getUserData("IsAspireHost")
        if (isAspireHost?.equals("true", true) != true) {
            event.presentation.isEnabledAndVisible = false
            return
        }

        event.presentation.isEnabledAndVisible = true
    }

    override fun getActionUpdateThread() = ActionUpdateThread.BGT
}