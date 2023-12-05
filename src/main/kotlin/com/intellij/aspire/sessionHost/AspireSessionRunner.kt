package com.intellij.aspire.sessionHost

import com.intellij.aspire.generated.*
import com.intellij.execution.ProgramRunnerUtil
import com.intellij.execution.RunManager
import com.intellij.execution.RunnerAndConfigurationSettings
import com.intellij.execution.configurations.ConfigurationTypeUtil
import com.intellij.execution.executors.DefaultDebugExecutor
import com.intellij.execution.executors.DefaultRunExecutor
import com.intellij.execution.process.ProcessAdapter
import com.intellij.execution.process.ProcessEvent
import com.intellij.execution.process.ProcessOutputType
import com.intellij.execution.runners.ExecutionEnvironmentBuilder
import com.intellij.execution.runners.ProgramRunner
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.rd.util.launchOnUi
import com.intellij.openapi.util.Key
import com.intellij.util.execution.ParametersListUtil
import com.intellij.util.io.systemIndependentPath
import com.jetbrains.rd.util.lifetime.Lifetime
import com.jetbrains.rider.model.RunnableProject
import com.jetbrains.rider.model.runnableProjectsModel
import com.jetbrains.rider.projectView.solution
import com.jetbrains.rider.run.configurations.project.DotNetProjectConfiguration
import com.jetbrains.rider.run.configurations.project.DotNetProjectConfigurationType
import com.jetbrains.rider.run.pid
import kotlin.io.path.Path

@Service(Service.Level.PROJECT)
class AspireSessionRunner(private val project: Project) {
    companion object {
        fun getInstance(project: Project): AspireSessionRunner = project.service()

        private val LOG = logger<AspireSessionRunner>()

        private const val ASPIRE_SUFFIX = "Aspire"
    }

    fun runSession(
        id: String,
        session: SessionModel,
        sessionLifetime: Lifetime,
        hostId: String,
        model: AspireSessionHostModel,
        hostLifetime: Lifetime
    ) {
        LOG.info("Starting a session for the project ${session.projectPath}")

        val host = AspireSessionHostService.getInstance().getHost(hostId)
        if (host == null) {
            LOG.warn("Unable to find Aspire host with id=$hostId")
            return
        }

        val configuration = getOrCreateConfiguration(session, host)
        if (configuration == null) {
            LOG.warn("Unable to find or create run configuration for the project ${session.projectPath}")
            return
        }

        val isDebug = host.isDebug || session.debug
        val executor =
            if (!isDebug) DefaultRunExecutor.getRunExecutorInstance()
            else DefaultDebugExecutor.getDebugExecutorInstance()

        val environment = ExecutionEnvironmentBuilder
            .create(project, executor, configuration.configuration)
            .build()

        environment.callback = ProgramRunner.Callback {
            val processHandler = it?.processHandler
            if (processHandler != null) {
                val pid = processHandler.pid()
                if (pid != null) {
                    hostLifetime.launchOnUi {
                        model.processStarted.fire(ProcessStarted(id, pid))
                    }
                }

                val processAdapter = object : ProcessAdapter() {
                    override fun onTextAvailable(event: ProcessEvent, outputType: Key<*>) {
                        hostLifetime.launchOnUi {
                            model.logReceived.fire(
                                LogReceived(id, outputType == ProcessOutputType.STDERR, event.text)
                            )
                        }
                    }

                    override fun processTerminated(event: ProcessEvent) {
                        hostLifetime.launchOnUi {
                            model.processTerminated.fire(ProcessTerminated(id, event.exitCode))
                        }
                    }
                }

                sessionLifetime.onTermination {
                    if (!processHandler.isProcessTerminating && !processHandler.isProcessTerminated) {
                        processHandler.destroyProcess()
                    }
                }

                processHandler.addProcessListener(processAdapter)
            }
        }

        ProgramRunnerUtil.executeConfiguration(environment, false, true)
    }

    private fun getOrCreateConfiguration(
        session: SessionModel,
        host: AspireSessionHostService.HostConfiguration
    ): RunnerAndConfigurationSettings? {
        val projects = project.solution.runnableProjectsModel.projects.valueOrNull
        if (projects == null) {
            LOG.warn("Runnable projects model doesn't contain projects")
            return null
        }

        val projectPath = Path(session.projectPath).systemIndependentPath
        val runnableProject = projects.firstOrNull {
            DotNetProjectConfigurationType.isTypeApplicable(it.kind) && it.projectFilePath == projectPath
        }
        if (runnableProject == null) {
            LOG.warn("Unable to find a specified runnable project")
            return null
        }

        val runManager = RunManager.getInstance(project)
        val configurationType = ConfigurationTypeUtil.findConfigurationType(DotNetProjectConfigurationType::class.java)
        val existingConfiguration = runManager.allSettings.firstOrNull {
            it.type.id == configurationType.id && it.name.endsWith(ASPIRE_SUFFIX) && it.folderName == host.projectName
        }

        if (existingConfiguration != null) {
            LOG.trace("Found existing configurations: ${existingConfiguration.name}")
            return updateConfiguration(existingConfiguration, runnableProject, session)
        }

        LOG.trace("Creating a new configuration")
        return createConfiguration(configurationType, runManager, runnableProject, session, host)
    }

    private fun updateConfiguration(
        existingConfiguration: RunnerAndConfigurationSettings,
        runnableProject: RunnableProject,
        session: SessionModel,
    ): RunnerAndConfigurationSettings {
        existingConfiguration.apply {
            (configuration as DotNetProjectConfiguration).apply {
                parameters.projectFilePath = runnableProject.projectFilePath
                parameters.projectKind = runnableProject.kind
                parameters.programParameters = ParametersListUtil.join(session.args?.toList() ?: emptyList())
                parameters.envs = session.envs?.associate { it.key to it.value } ?: emptyMap()
            }
        }

        return existingConfiguration
    }

    private fun createConfiguration(
        configurationType: DotNetProjectConfigurationType,
        runManager: RunManager,
        runnableProject: RunnableProject,
        session: SessionModel,
        host: AspireSessionHostService.HostConfiguration
    ): RunnerAndConfigurationSettings {
        val factory = configurationType.factory
        val defaultConfiguration =
            runManager.createConfiguration("${runnableProject.name}-$ASPIRE_SUFFIX", factory).apply {
                (configuration as DotNetProjectConfiguration).apply {
                    parameters.projectFilePath = runnableProject.projectFilePath
                    parameters.projectKind = runnableProject.kind
                    parameters.programParameters = ParametersListUtil.join(session.args?.toList() ?: emptyList())
                    parameters.envs = session.envs?.associate { it.key to it.value } ?: emptyMap()
                }
                isActivateToolWindowBeforeRun = false
                isFocusToolWindowBeforeRun = false
                folderName = host.projectName
            }

        runManager.addConfiguration(defaultConfiguration)

        return defaultConfiguration
    }
}