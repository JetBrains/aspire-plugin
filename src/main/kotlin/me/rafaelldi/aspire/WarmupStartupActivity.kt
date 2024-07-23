package me.rafaelldi.aspire

import com.intellij.openapi.application.EDT
import com.intellij.openapi.project.Project
import com.intellij.openapi.rd.util.lifetime
import com.intellij.openapi.startup.ProjectActivity
import com.intellij.util.application
import com.jetbrains.rd.util.reactive.adviseOnce
import com.jetbrains.rider.run.configurations.runnableProjectsModelIfAvailable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.rafaelldi.aspire.run.FunctionLaunchProfilesService

class WarmupStartupActivity: ProjectActivity {
    override suspend fun execute(project: Project) {
        withContext(Dispatchers.EDT) {
            project.runnableProjectsModelIfAvailable?.projects?.adviseOnce(project.lifetime) {
                application.runReadAction {
                    FunctionLaunchProfilesService.getInstance(project).initialize(it)
                }
            }
        }
    }
}