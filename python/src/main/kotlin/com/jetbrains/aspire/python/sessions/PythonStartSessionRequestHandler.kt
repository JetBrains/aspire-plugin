@file:Suppress("LoggingSimilarMessage", "UnstableApiUsage")

package com.jetbrains.aspire.python.sessions

import com.intellij.execution.executors.DefaultDebugExecutor
import com.intellij.execution.executors.DefaultRunExecutor
import com.intellij.execution.process.BaseProcessHandler
import com.intellij.execution.process.ProcessEvent
import com.intellij.execution.process.ProcessListener
import com.intellij.execution.process.ProcessOutputType
import com.intellij.execution.runners.ExecutionEnvironmentBuilder
import com.intellij.execution.runners.ProgramRunner
import com.intellij.execution.ui.RunContentDescriptor
import com.intellij.openapi.application.EDT
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.diagnostic.trace
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.util.application
import com.jetbrains.aspire.sessions.*
import com.jetbrains.python.run.PythonCommandLineState
import com.jetbrains.rd.util.lifetime.Lifetime
import com.jetbrains.rd.util.lifetime.LifetimeDefinition
import com.jetbrains.rd.util.lifetime.isAlive
import com.jetbrains.rd.util.lifetime.isNotAlive
import com.jetbrains.rd.util.threading.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.withContext
import java.net.InetAddress
import java.net.ServerSocket
import kotlin.io.path.absolutePathString

internal class PythonStartSessionRequestHandler : StartSessionRequestHandler {
    companion object {
        private val LOG = logger<PythonStartSessionRequestHandler>()
    }

    override fun isApplicable(request: StartSessionRequest): Boolean =
        request.launchConfiguration is PythonSessionLaunchConfiguration

    override suspend fun handleRequests(requests: List<StartSessionRequest>, project: Project) {
        LOG.trace { "Received ${requests.size} Python start session request(s)" }

        requests.forEach {
            handleStartSessionRequest(it, project)
        }
    }

    private fun handleStartSessionRequest(request: StartSessionRequest, project: Project) {
        LOG.info("Creating Python session ${request.sessionId}")

        val launchConfiguration = request.launchConfiguration as? PythonSessionLaunchConfiguration
        if (launchConfiguration == null) {
            LOG.warn("Launch configuration is not a PythonSessionLaunchConfiguration: ${request.launchConfiguration::class.simpleName}")
            return
        }

        logLaunchConfiguration(launchConfiguration)

        val sessionLifetime = request.sessionLifetime.lifetime
        sessionLifetime.launch {
            val sessionProcessListener = createSessionProcessEventListener(
                request.sessionId,
                request.sessionEvents,
                request.sessionLifetime
            )

            launchSessionProcess(
                request.sessionId,
                launchConfiguration,
                sessionProcessListener,
                sessionLifetime,
                project
            )
        }
    }

    private fun logLaunchConfiguration(launchConfiguration: PythonSessionLaunchConfiguration) {
        LOG.trace { "Session program path: ${launchConfiguration.programPath}" }
        LOG.trace { "Session debug flag: ${launchConfiguration.debug}" }
        LOG.trace { "Session interpreter path: ${launchConfiguration.interpreterPath}" }
        LOG.trace { "Session module: ${launchConfiguration.module}" }
        LOG.trace { "Session args: ${launchConfiguration.args?.joinToString(", ")}" }
        LOG.trace { "Session env keys: ${launchConfiguration.envs?.joinToString(", ") { it.first }}" }
    }

    private fun createSessionProcessEventListener(
        sessionId: String,
        sessionEvents: Channel<SessionEvent>,
        processLifetimeDefinition: LifetimeDefinition
    ): ProcessListener =
        object : ProcessListener {
            override fun startNotified(event: ProcessEvent) {
                LOG.info("Session $sessionId process was started")
                val pid = (event.processHandler as? BaseProcessHandler<*>)?.process?.pid()
                if (pid == null) {
                    LOG.warn("Unable to determine process id for the session $sessionId")
                    terminateSession(-1)
                } else {
                    LOG.trace { "Session $sessionId process id = $pid" }
                    val eventSendingResult = sessionEvents.trySend(SessionProcessStarted(sessionId, pid))
                    if (!eventSendingResult.isSuccess) {
                        LOG.warn("Unable to send an event for session $sessionId start")
                    }
                }
            }

            override fun onTextAvailable(event: ProcessEvent, outputType: Key<*>) {
                val isStdErr = outputType == ProcessOutputType.STDERR
                val eventSendingResult = sessionEvents.trySend(SessionLogReceived(sessionId, isStdErr, event.text))
                if (!eventSendingResult.isSuccess) {
                    LOG.warn("Unable to send an event for session $sessionId log")
                }
            }

            override fun processNotStarted() {
                LOG.warn("Session $sessionId process is not started")
                terminateSession(-1)
            }

            override fun processTerminated(event: ProcessEvent) {
                LOG.info("Session $sessionId process was terminated (${event.exitCode}, ${event.text})")
                terminateSession(event.exitCode)
            }

            private fun terminateSession(exitCode: Int) {
                LOG.trace { "Terminating session $sessionId with exitCode $exitCode" }
                val eventSendingResult = sessionEvents.trySend(SessionProcessTerminated(sessionId, exitCode))
                if (!eventSendingResult.isSuccess) {
                    LOG.warn("Unable to send an event for session $sessionId termination")
                }
                if (processLifetimeDefinition.isAlive) {
                    application.invokeLater {
                        LOG.trace { "Terminating session $sessionId lifetime" }
                        processLifetimeDefinition.terminate()
                    }
                }
            }
        }

    private suspend fun launchSessionProcess(
        sessionId: String,
        launchConfiguration: PythonSessionLaunchConfiguration,
        sessionProcessEventListener: ProcessListener,
        sessionProcessLifetime: Lifetime,
        project: Project
    ) {
        LOG.info("Starting a session process for ${launchConfiguration.programPath}")

        if (sessionProcessLifetime.isNotAlive) {
            LOG.warn("Unable to run ${launchConfiguration.programPath} because lifetimes are not alive")
            return
        }

        val preferred = SessionLaunchPreferenceService
            .getInstance(project)
            .getPreferredLaunchMode(launchConfiguration.programPath.absolutePathString())
        val shouldDebug = when (preferred) {
            SessionLaunchMode.DEBUG -> true
            SessionLaunchMode.RUN -> false
            null -> launchConfiguration.debug
        }

        if (shouldDebug) {
            val serverSocket = withContext(Dispatchers.IO) {
                PythonCommandLineState.createServerSocket()
            }
            val profile = PythonSessionDebugProfile(
                sessionId,
                launchConfiguration,
                sessionProcessEventListener,
                sessionProcessLifetime,
                serverSocket
            )
            executeProfile(profile, true, project)
        } else {
            val profile = PythonSessionRunProfile(
                sessionId,
                launchConfiguration,
                sessionProcessEventListener,
                sessionProcessLifetime
            )
            executeProfile(profile, false, project)
        }
    }

    private suspend fun executeProfile(
        profile: com.intellij.execution.configurations.RunProfile,
        isDebugSession: Boolean,
        project: Project
    ) {
        val environment = if (!isDebugSession) {
            ExecutionEnvironmentBuilder
                .createOrNull(project, DefaultRunExecutor.getRunExecutorInstance(), profile)
                ?.runner(ProgramRunner.findRunnerById(PythonSessionProgramRunner.ID) ?: return)
                ?.build()
        } else {
            ExecutionEnvironmentBuilder
                .createOrNull(project, DefaultDebugExecutor.getDebugExecutorInstance(), profile)
                ?.runner(ProgramRunner.findRunnerById(PythonSessionDebugProgramRunner.ID) ?: return)
                ?.build()
        }

        if (environment == null) {
            LOG.warn("Unable to create execution environment")
            return
        }

        environment.assignNewExecutionId()
        environment.callback = object : ProgramRunner.Callback {
            override fun processStarted(runContentDescriptor: RunContentDescriptor?) {
                runContentDescriptor?.apply {
                    isActivateToolWindowWhenAdded = false
                    isAutoFocusContent = false
                }
            }
        }

        withContext(Dispatchers.EDT) {
            environment.runner.execute(environment)
        }
    }
}
