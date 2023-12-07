package me.rafaelldi.aspire.sessionHost

import com.intellij.execution.ProgramRunnerUtil
import com.intellij.execution.RunManager
import com.intellij.execution.RunnerAndConfigurationSettings
import com.intellij.execution.configurations.ConfigurationTypeUtil
import com.intellij.execution.executors.DefaultDebugExecutor
import com.intellij.execution.executors.DefaultRunExecutor
import com.intellij.execution.process.AnsiEscapeDecoder
import com.intellij.execution.process.ProcessAdapter
import com.intellij.execution.process.ProcessEvent
import com.intellij.execution.process.ProcessOutputType
import com.intellij.execution.runners.ExecutionEnvironmentBuilder
import com.intellij.execution.runners.ProgramRunner
import com.intellij.execution.ui.RunContentDescriptor
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.rd.util.launchOnUi
import com.intellij.openapi.rd.util.withUiContext
import com.intellij.openapi.util.Key
import com.intellij.util.execution.ParametersListUtil
import com.intellij.util.io.systemIndependentPath
import com.jetbrains.rd.util.lifetime.Lifetime
import com.jetbrains.rd.util.lifetime.isNotAlive
import com.jetbrains.rider.model.RunnableProject
import com.jetbrains.rider.model.runnableProjectsModel
import com.jetbrains.rider.projectView.solution
import com.jetbrains.rider.run.configurations.project.DotNetProjectConfiguration
import com.jetbrains.rider.run.configurations.project.DotNetProjectConfigurationType
import com.jetbrains.rider.run.pid
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.consumeAsFlow
import kotlinx.coroutines.launch
import me.rafaelldi.aspire.generated.*
import kotlin.io.path.Path

@Service(Service.Level.PROJECT)
class AspireSessionRunner(private val project: Project, scope: CoroutineScope) {
    companion object {
        fun getInstance(project: Project): AspireSessionRunner = project.service()

        private val LOG = logger<AspireSessionRunner>()

        private const val ASPIRE_SUFFIX = "Aspire"
    }

    private val commandChannel = Channel<RunSessionCommand>(Channel.UNLIMITED)
    private val ansiEscapeDecoder = AnsiEscapeDecoder()

    data class RunSessionCommand(
        val sessionId: String,
        val sessionModel: SessionModel,
        val sessionLifetime: Lifetime,
        val hostId: String,
        val hostModel: AspireSessionHostModel,
        val hostLifetime: Lifetime
    )

    init {
        scope.launch {
            commandChannel.consumeAsFlow().collect {
                runSession(
                    it.sessionId,
                    it.sessionModel,
                    it.sessionLifetime,
                    it.hostId,
                    it.hostModel,
                    it.hostLifetime
                )
            }
        }
    }

    fun runSession(command: RunSessionCommand) {
        commandChannel.trySend(command)
    }

    private suspend fun runSession(
        sessionId: String,
        sessionModel: SessionModel,
        sessionLifetime: Lifetime,
        hostId: String,
        hostModel: AspireSessionHostModel,
        hostLifetime: Lifetime
    ) {
        LOG.info("Starting a session for the project ${sessionModel.projectPath}")

        if (hostLifetime.isNotAlive || sessionLifetime.isNotAlive) {
            LOG.info("Unable to run project ${sessionModel.projectPath} because lifetimes are not alive")
            return
        }

        val host = AspireSessionHostService.getInstance().getHost(hostId)
        if (host == null) {
            LOG.warn("Unable to find Aspire host with id=$hostId")
            return
        }

        val configuration = getOrCreateConfiguration(sessionModel, host)
        if (configuration == null) {
            LOG.warn("Unable to find or create run configuration for the project ${sessionModel.projectPath}")
            return
        }

        val isDebug = host.isDebug || sessionModel.debug
        val executor =
            if (!isDebug) DefaultRunExecutor.getRunExecutorInstance()
            else DefaultDebugExecutor.getDebugExecutorInstance()

        val environment = ExecutionEnvironmentBuilder
            .create(project, executor, configuration.configuration)
            .build()

        val started = CompletableDeferred<Boolean>()
        withUiContext {
            ProgramRunnerUtil.executeConfigurationAsync(environment, false, true, object : ProgramRunner.Callback {
                override fun processStarted(descriptor: RunContentDescriptor?) {
                    val processHandler = descriptor?.processHandler
                    if (processHandler != null) {
                        val pid = processHandler.pid()
                        if (pid != null) {
                            hostLifetime.launchOnUi {
                                hostModel.processStarted.fire(ProcessStarted(sessionId, pid))
                            }
                        }

                        val processAdapter = object : ProcessAdapter() {
                            override fun onTextAvailable(event: ProcessEvent, outputType: Key<*>) {
                                val text = decodeAnsiCommandsToString(event.text, outputType)
                                hostLifetime.launchOnUi {
                                    hostModel.logReceived.fire(
                                        LogReceived(sessionId, outputType == ProcessOutputType.STDERR, text)
                                    )
                                }
                            }

                            override fun processTerminated(event: ProcessEvent) {
                                hostLifetime.launchOnUi {
                                    hostModel.processTerminated.fire(ProcessTerminated(sessionId, event.exitCode))
                                }
                            }

                            override fun processNotStarted() {
                                hostLifetime.launchOnUi {
                                    hostModel.processTerminated.fire(ProcessTerminated(sessionId, -1))
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

                    started.complete(true)
                }

                override fun processNotStarted() {
                    started.complete(false)
                }
            })
        }

        if (!started.await()) {
            LOG.warn("Unable to start run configuration for the project ${sessionModel.projectPath}")
        }
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
        val configurationName = "${runnableProject.name}-$ASPIRE_SUFFIX"
        val existingConfiguration = runManager.allSettings.firstOrNull {
            it.type.id == configurationType.id && it.name == configurationName && it.folderName == host.projectName
        }

        if (existingConfiguration != null) {
            LOG.trace("Found existing configurations: ${existingConfiguration.name}")
            return updateConfiguration(existingConfiguration, runnableProject, session)
        }

        LOG.trace("Creating a new configuration")
        return createConfiguration(configurationType, configurationName, runManager, runnableProject, session, host)
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
        configurationName: String,
        runManager: RunManager,
        runnableProject: RunnableProject,
        session: SessionModel,
        host: AspireSessionHostService.HostConfiguration
    ): RunnerAndConfigurationSettings {
        val factory = configurationType.factory
        val defaultConfiguration =
            runManager.createConfiguration(configurationName, factory).apply {
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

    private fun decodeAnsiCommandsToString(ansi: String, outputType: Key<*>): String {
        val buffer = StringBuilder()
        ansiEscapeDecoder.escapeText(ansi, outputType) { text, _ -> buffer.append(text) }
        return buffer.toString()
    }
}