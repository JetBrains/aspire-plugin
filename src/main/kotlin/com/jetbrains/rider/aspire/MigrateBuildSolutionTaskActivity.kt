package com.jetbrains.rider.aspire

import com.intellij.execution.RunManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import com.jetbrains.rider.build.tasks.BuildProjectBeforeRunTask
import com.jetbrains.rider.build.tasks.BuildSolutionBeforeRunTaskProvider

class MigrateBuildSolutionTaskActivity : ProjectActivity {
    override suspend fun execute(project: Project) {
        RunManager.getInstance(project).allSettings.forEach { setting ->
            if (setting.type.id == "AspireHostConfiguration") {
                val buildSolutionTasks = setting.configuration.beforeRunTasks.filter {
                    it.providerId == BuildSolutionBeforeRunTaskProvider.providerId
                }
                if (buildSolutionTasks.isNotEmpty()) {
                    buildSolutionTasks.forEach {
                        setting.configuration.beforeRunTasks.remove(it)
                    }
                    val buildSolutionTask = BuildProjectBeforeRunTask()
                    buildSolutionTask.isEnabled = true
                    setting.configuration.beforeRunTasks.add(0, buildSolutionTask)
                }
            }
        }
    }
}