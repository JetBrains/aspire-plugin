package me.rafaelldi.aspire.run

import com.intellij.execution.RunManager
import com.intellij.ide.util.RunOnceUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity

class DisableToolWindowActivity : ProjectActivity {
    override suspend fun execute(project: Project) {
        RunOnceUtil.runOnceForProject(project, "Disable tool window activation for Aspire run config") {
            RunManager.getInstance(project).allSettings.forEach {
                if (it.type.id == "AspireHostConfiguration") {
                    it.isActivateToolWindowBeforeRun = false
                    it.isFocusToolWindowBeforeRun = false
                }
            }
        }
    }
}