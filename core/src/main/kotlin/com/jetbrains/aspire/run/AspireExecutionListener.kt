package com.jetbrains.aspire.run

import com.intellij.execution.ExecutionListener
import com.intellij.execution.configurations.RunProfile
import com.intellij.execution.process.ProcessEvent
import com.intellij.execution.process.ProcessHandler
import com.intellij.execution.process.ProcessListener
import com.intellij.execution.process.ProcessOutputType
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.jetbrains.aspire.worker.AppHostListener
import com.jetbrains.aspire.worker.AppHostLogEntry
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
abstract class AspireExecutionListener(private val project: Project) : ExecutionListener {
    companion object {
        private val LOG = logger<AspireExecutionListener>()
        private const val LOG_REPLAY_CAPACITY = 100
    }

    protected abstract fun isValid(profile: RunProfile): Boolean

    protected abstract fun getProcessHandler(handler: ProcessHandler): ProcessHandler

    override fun processStarted(
        executorId: String,
        env: ExecutionEnvironment,
        handler: ProcessHandler
    ) {
        val profile = env.runProfile
        if (profile !is AspireRunConfiguration) return
        if (!isValid(profile)) return

        val appHostFilePath = profile.appHostFilePath
        if (appHostFilePath == null) {
            LOG.warn("Aspire run configuration '${profile.name}' started without an AppHost file path")
            return
        }
        val processHandler = getProcessHandler(handler)
        val logFlow = MutableSharedFlow<AppHostLogEntry>(
            replay = LOG_REPLAY_CAPACITY,
            onBufferOverflow = BufferOverflow.DROP_OLDEST
        )
        processHandler.addProcessListener(object : ProcessListener {
            override fun onTextAvailable(event: ProcessEvent, outputType: Key<*>) {
                logFlow.tryEmit(AppHostLogEntry(event.text, outputType == ProcessOutputType.STDERR))
            }
        })

        project.messageBus
            .syncPublisher(AppHostListener.TOPIC)
            .appHostStarted(appHostFilePath, profile.name, logFlow.asSharedFlow())
    }

    override fun processTerminated(
        executorId: String,
        env: ExecutionEnvironment,
        handler: ProcessHandler,
        exitCode: Int
    ) {
        val profile = env.runProfile
        if (!isValid(profile)) return

        val appHostFilePath = (profile as AspireRunConfiguration).appHostFilePath
        if (appHostFilePath == null) {
            LOG.warn("Aspire run configuration '${profile.name}' terminated without an AppHost file path")
            return
        }

        project.messageBus
            .syncPublisher(AppHostListener.TOPIC)
            .appHostStopped(appHostFilePath)
    }
}
