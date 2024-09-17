package com.jetbrains.rider.aspire.sessionHost.projectLaunchers

import com.intellij.execution.process.KillableProcessHandler
import com.intellij.execution.process.ProcessAdapter
import com.intellij.execution.process.ProcessEvent
import com.intellij.execution.process.ProcessHandler
import com.intellij.execution.process.ProcessOutputType
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.jetbrains.rider.aspire.generated.SessionModel
import com.jetbrains.rider.aspire.sessionHost.SessionEvent
import com.jetbrains.rider.aspire.sessionHost.SessionExecutableFactory
import com.jetbrains.rider.aspire.sessionHost.SessionLogReceived
import com.jetbrains.rider.aspire.sessionHost.SessionStarted
import com.jetbrains.rider.aspire.sessionHost.SessionTerminated
import com.jetbrains.rider.aspire.util.decodeAnsiCommandsToString
import com.jetbrains.rider.run.configurations.RunnableProjectKinds
import com.jetbrains.rider.run.pid
import com.jetbrains.rider.runtime.DotNetExecutable
import com.jetbrains.rider.runtime.DotNetRuntime
import com.jetbrains.rider.runtime.RiderDotNetActiveRuntimeHost
import com.jetbrains.rider.runtime.dotNetCore.DotNetCoreRuntime
import kotlinx.coroutines.flow.MutableSharedFlow

private val LOG = Logger.getInstance("#com.jetbrains.rider.aspire.sessionHost.projectLaunchers.ProjectLauncherUtils")

suspend fun getExecutable(sessionModel: SessionModel, project: Project): DotNetExecutable? {
    val factory = SessionExecutableFactory.getInstance(project)
    val executable = factory.createExecutable(sessionModel)
    if (executable == null) {
        LOG.warn("Unable to create executable for project: ${sessionModel.projectPath}")
    }

    return executable
}

fun getRuntime(executable: DotNetExecutable, project: Project): DotNetCoreRuntime? {
    val runtime = DotNetRuntime.detectRuntimeForProject(
        project,
        RunnableProjectKinds.DotNetCore,
        RiderDotNetActiveRuntimeHost.getInstance(project),
        executable.runtimeType,
        executable.exePath,
        executable.projectTfm
    )?.runtime as? DotNetCoreRuntime
    if (runtime == null) {
        LOG.warn("Unable to detect runtime for executable: ${executable.exePath}")
    }

    return runtime
}

fun subscribeToSessionEvents(
    sessionId: String,
    handler: ProcessHandler,
    sessionEvents: MutableSharedFlow<SessionEvent>
) {
    handler.addProcessListener(object : ProcessAdapter() {
        override fun startNotified(event: ProcessEvent) {
            LOG.trace("Aspire session process started (id: $sessionId)")
            val pid = when (event.processHandler) {
                is KillableProcessHandler -> event.processHandler.pid()
                else -> null
            }
            if (pid == null) {
                LOG.warn("Unable to determine process id for the session $sessionId")
                sessionEvents.tryEmit(SessionTerminated(sessionId, -1))
            } else {
                sessionEvents.tryEmit(SessionStarted(sessionId, pid))
            }
        }

        override fun onTextAvailable(event: ProcessEvent, outputType: Key<*>) {
            val text = decodeAnsiCommandsToString(event.text, outputType)
            val isStdErr = outputType == ProcessOutputType.STDERR
            sessionEvents.tryEmit(SessionLogReceived(sessionId, isStdErr, text))
        }

        override fun processNotStarted() {
            LOG.warn("Aspire session process is not started")
            sessionEvents.tryEmit(SessionTerminated(sessionId, -1))
        }
    })
}