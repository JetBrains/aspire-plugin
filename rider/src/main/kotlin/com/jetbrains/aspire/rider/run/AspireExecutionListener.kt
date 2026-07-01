package com.jetbrains.aspire.rider.run

import com.intellij.execution.ExecutionListener
import com.intellij.execution.process.ProcessEvent
import com.intellij.execution.process.ProcessHandler
import com.intellij.execution.process.ProcessListener
import com.intellij.execution.process.ProcessOutputType
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.jetbrains.aspire.worker.AppHostListener
import com.jetbrains.aspire.worker.AppHostLogEntry
import com.jetbrains.rider.debugger.DebuggerWorkerProcessHandler
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlin.io.path.Path

internal class AspireExecutionListener(private val project: Project) : ExecutionListener {
    companion object {
        private const val LOG_REPLAY_CAPACITY = 100
    }

    override fun processStarted(
        executorId: String,
        env: ExecutionEnvironment,
        handler: ProcessHandler
    ) {
        val profile = env.runProfile
        if (profile !is AspireRunConfiguration) return

        val appHostFilePath = Path(profile.parameters.appHostFilePath)
        val runConfigName = profile.name

        val realHandler =
            if (handler is DebuggerWorkerProcessHandler) handler.debuggerWorkerRealHandler
            else handler
        val logFlow = MutableSharedFlow<AppHostLogEntry>(
            replay = LOG_REPLAY_CAPACITY,
            onBufferOverflow = BufferOverflow.DROP_OLDEST
        )
        realHandler.addProcessListener(object : ProcessListener {
            override fun onTextAvailable(event: ProcessEvent, outputType: Key<*>) {
                logFlow.tryEmit(AppHostLogEntry(event.text, outputType == ProcessOutputType.STDERR))
            }
        })

        project.messageBus
            .syncPublisher(AppHostListener.TOPIC)
            .appHostStarted(appHostFilePath, runConfigName, logFlow.asSharedFlow())
    }

    override fun processTerminated(
        executorId: String,
        env: ExecutionEnvironment,
        handler: ProcessHandler,
        exitCode: Int
    ) {
        val profile = env.runProfile
        if (profile !is AspireRunConfiguration) return

        val appHostFilePath = Path(profile.parameters.appHostFilePath)

        project.messageBus
            .syncPublisher(AppHostListener.TOPIC)
            .appHostStopped(appHostFilePath)
    }
}
