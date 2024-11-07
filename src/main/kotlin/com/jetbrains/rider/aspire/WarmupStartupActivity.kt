package com.jetbrains.rider.aspire

import com.intellij.openapi.application.EDT
import com.intellij.openapi.project.Project
import com.intellij.openapi.rd.util.lifetime
import com.intellij.openapi.startup.ProjectActivity
import com.jetbrains.rd.util.threading.coroutines.launch
import com.jetbrains.rider.aspire.run.FunctionLaunchProfilesService
import com.jetbrains.rider.run.configurations.runnableProjectsModelIfAvailable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class WarmupStartupActivity: ProjectActivity {
    override suspend fun execute(project: Project) {
        withContext(Dispatchers.EDT) {
            project.runnableProjectsModelIfAvailable?.projects?.view(project.lifetime) { lt, projects ->
                lt.launch(Dispatchers.Default) {
                    FunctionLaunchProfilesService.getInstance(project).initialize(projects)
                }
            }
        }
    }
}
