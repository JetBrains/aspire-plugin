package me.rafaelldi.aspire.run

import com.intellij.execution.RunManager
import com.intellij.ide.util.RunOnceUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import com.jetbrains.rider.build.tasks.BuildProjectBeforeRunTaskProvider
import com.jetbrains.rider.build.tasks.BuildSolutionBeforeRunTask

class RemoveBuildSolutionTaskActivity : ProjectActivity {
    override suspend fun execute(project: Project) {
        RunOnceUtil.runOnceForProject(project, "Remove BuildProject before run task for Aspire run config") {
            RunManager.getInstance(project).allSettings.forEach { setting ->
                if (setting.type.id == "AspireHostConfiguration") {
                    val buildProjectTasks = setting.configuration.beforeRunTasks.filter {
                        it.providerId == BuildProjectBeforeRunTaskProvider.providerId
                    }
                    if (buildProjectTasks.isNotEmpty()) {
                        buildProjectTasks.forEach {
                            setting.configuration.beforeRunTasks.remove(it)
                        }

                        val buildSolutionTask = BuildSolutionBeforeRunTask()
                        buildSolutionTask.isEnabled = true
                        setting.configuration.beforeRunTasks.add(buildSolutionTask)
                    }
                }
            }
        }
    }
}