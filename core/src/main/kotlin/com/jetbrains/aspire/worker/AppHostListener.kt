package com.jetbrains.aspire.worker

import com.intellij.util.messages.Topic
import com.jetbrains.aspire.worker.AspireAppHost.AppHostEnvironment
import kotlinx.coroutines.flow.SharedFlow
import org.jetbrains.annotations.ApiStatus
import java.nio.file.Path

/**
 * Project-level listener for Aspire AppHost lifecycle transitions.
 *
 * Events are published when the process has started and when the process has terminated.
 */
@ApiStatus.Internal
interface AppHostListener {
    companion object {
        @Topic.ProjectLevel
        val TOPIC = Topic.create("Aspire AppHost Listener", AppHostListener::class.java)
    }

    /**
     * Notifies that an Aspire AppHost process has started.
     *
     * @param environment connection metadata prepared for this process launch
     * @param logFlow buffered stream of the AppHost process output (text + stderr flag)
     */
    fun appHostStarted(appHostFile: Path, environment: AppHostEnvironment, logFlow: SharedFlow<AppHostLogEntry>)

    /**
     * Notifies that an Aspire AppHost process has stopped.
     */
    fun appHostStopped(appHostFile: Path)
}
