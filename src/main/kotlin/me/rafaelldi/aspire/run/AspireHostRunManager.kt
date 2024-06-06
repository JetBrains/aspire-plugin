package me.rafaelldi.aspire.run

import com.intellij.execution.ProgramRunnerUtil
import com.intellij.execution.RunManager
import com.intellij.execution.executors.DefaultDebugExecutor
import com.intellij.execution.executors.DefaultRunExecutor
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import me.rafaelldi.aspire.services.AspireHostService
import kotlin.io.path.Path

@Service(Service.Level.PROJECT)
class AspireHostRunManager(private val project: Project) {
    companion object {
        fun getInstance(project: Project) = project.service<AspireHostRunManager>()
    
        private val LOG = logger<AspireHostRunManager>()
    }

    fun executeConfigurationForHost(host: AspireHostService, underDebug: Boolean) {
        val executor = if (underDebug) DefaultDebugExecutor.getDebugExecutorInstance() else DefaultRunExecutor.getRunExecutorInstance()
        val runManager = RunManager.getInstance(project)
        val selected = runManager.selectedConfiguration
        val selectedConfiguration = selected?.configuration
        if (selectedConfiguration != null && selectedConfiguration is AspireHostConfiguration) {
            if (host.projectPath == Path(selectedConfiguration.parameters.projectFilePath)) {
                ProgramRunnerUtil.executeConfiguration(selected, executor)
                return
            }
        }

        val configuration = runManager.getConfigurationSettingsList(AspireHostConfigurationType::class.java)
            .firstOrNull {
                val path = (it.configuration as? AspireHostConfiguration)?.parameters?.projectFilePath ?: return@firstOrNull false
                Path(path) == host.projectPath
            }
        if (configuration != null) {
            ProgramRunnerUtil.executeConfiguration(configuration, executor)
        }
    }
}