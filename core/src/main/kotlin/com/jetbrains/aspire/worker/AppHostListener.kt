package com.jetbrains.aspire.worker

import com.intellij.util.messages.Topic
import com.jetbrains.aspire.worker.AspireAppHost.AppHostEnvironment
import kotlinx.coroutines.flow.SharedFlow
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
    fun appHostStarting(appHostFilePath: Path, environment: AppHostEnvironment)

    /**
     * Notifies that an Aspire AppHost process has started.
     *
     * @param logFlow buffered stream of the AppHost process output (text + stderr flag)
     */
    fun appHostStarted(appHostFilePath: Path, runConfigName: String?, logFlow: SharedFlow<AppHostLogEntry>)

    /**
     * Notifies that an Aspire AppHost process has stopped.
     */
    fun appHostStopped(appHostFilePath: Path)
}
