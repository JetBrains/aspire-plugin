package com.jetbrains.aspire.worker

import com.intellij.execution.process.ProcessHandler
import com.intellij.util.messages.Topic
import com.jetbrains.aspire.worker.AspireAppHost.AppHostEnvironment
import org.jetbrains.annotations.ApiStatus
import java.nio.file.Path

/**
 * Project-level listener for Aspire AppHost lifecycle transitions.
 *
 * Events are published as the host is prepared for launch, when the process has started,
 * and when the process has terminated.
 */
@ApiStatus.Internal
interface AppHostListener {
    companion object {
        @Topic.ProjectLevel
        val TOPIC = Topic.create("Aspire AppHost Listener", AppHostListener::class.java)
    }

    /**
     * Notifies that an Aspire AppHost is about to start and publishes the connection metadata
     * needed later by the started state.
     */
    fun appHostStarting(appHostMainFilePath: Path, environment: AppHostEnvironment)

    /**
     * Notifies that an Aspire AppHost process has started.
     */
    fun appHostStarted(appHostMainFilePath: Path, runConfigName: String?, processHandler: ProcessHandler)

    /**
     * Notifies that an Aspire AppHost process has stopped.
     */
    fun appHostStopped(appHostMainFilePath: Path)
}
