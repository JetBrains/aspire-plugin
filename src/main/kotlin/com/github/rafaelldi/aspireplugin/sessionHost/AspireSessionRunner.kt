package com.github.rafaelldi.aspireplugin.sessionHost

import com.github.rafaelldi.aspireplugin.generated.SessionModel
import com.intellij.execution.ProgramRunnerUtil
import com.intellij.execution.RunManager
import com.intellij.execution.RunnerAndConfigurationSettings
import com.intellij.execution.configurations.ConfigurationTypeUtil
import com.intellij.execution.executors.DefaultDebugExecutor
import com.intellij.execution.executors.DefaultRunExecutor
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.util.execution.ParametersListUtil
import com.intellij.util.io.systemIndependentPath
import com.jetbrains.rider.model.runnableProjectsModel
import com.jetbrains.rider.projectView.solution
import com.jetbrains.rider.run.configurations.project.DotNetProjectConfiguration
import com.jetbrains.rider.run.configurations.project.DotNetProjectConfigurationType
import kotlin.io.path.Path

@Service(Service.Level.PROJECT)
class AspireSessionRunner(private val project: Project) {
    companion object {
        fun getInstance(project: Project): AspireSessionRunner = project.service()

        private val LOG = logger<AspireSessionRunner>()

        private const val ASPIRE_SUFFIX = "Aspire"
    }

    fun runSession(session: SessionModel) {
        LOG.info("Starting a project ${session.projectPath}")

        val configuration = getOrCreateConfiguration(session) ?: return
        val executor =
            if (!session.debug) DefaultRunExecutor.getRunExecutorInstance()
            else DefaultDebugExecutor.getDebugExecutorInstance()
        ProgramRunnerUtil.executeConfiguration(configuration, executor)
    }

    fun stopSession(session: SessionModel) {
        LOG.info("Stopping a project ${session.projectPath}")
    }

    private fun getOrCreateConfiguration(session: SessionModel): RunnerAndConfigurationSettings? {
        val projects = project.solution.runnableProjectsModel.projects.valueOrNull
        if (projects == null) {
            LOG.trace("Runnable projects model doesn't contain projects")
            return null
        }

        val projectPath = Path(session.projectPath).systemIndependentPath

        val runnableProject = projects.firstOrNull {
            DotNetProjectConfigurationType.isTypeApplicable(it.kind) && it.projectFilePath == projectPath
        }
        if (runnableProject == null) {
            LOG.trace("Unable to find a specified runnable project")
            return null
        }

        val runManager = RunManager.getInstance(project)

        val configurationType = ConfigurationTypeUtil.findConfigurationType(DotNetProjectConfigurationType::class.java)

        val existingConfiguration = runManager.allSettings.firstOrNull {
            it.type.id == configurationType.id && it.name.endsWith(ASPIRE_SUFFIX)
        }

        if (existingConfiguration != null) {
            LOG.trace("Found existing configurations: ${existingConfiguration.name}")
            return existingConfiguration
        }

        val factory = configurationType.factory
        val defaultConfiguration =
            runManager.createConfiguration("${runnableProject.name}-$ASPIRE_SUFFIX", factory).apply {
                (configuration as DotNetProjectConfiguration).apply {
                    parameters.projectFilePath = runnableProject.projectFilePath
                    parameters.projectKind = runnableProject.kind
                    parameters.programParameters = ParametersListUtil.join(session.args?.toList() ?: emptyList())
                    parameters.envs = session.envs?.associate { it.key to it.value } ?: emptyMap()
                }
                isTemporary = true
            }
        runManager.addConfiguration(defaultConfiguration)

        return defaultConfiguration
    }
}