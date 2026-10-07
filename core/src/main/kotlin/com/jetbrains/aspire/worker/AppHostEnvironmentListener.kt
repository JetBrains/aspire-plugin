package com.jetbrains.aspire.worker

import com.intellij.util.messages.Topic
import com.jetbrains.aspire.worker.AspireAppHost.AppHostEnvironment
import org.jetbrains.annotations.ApiStatus
import java.nio.file.Path

/**
 * Project-level listener for the connection metadata of an Aspire AppHost.
 *
 * The environment is published before the AppHost process is launched, so it is available
 * once the AppHost reports that it has started.
 */
@ApiStatus.Internal
interface AppHostEnvironmentListener {
    companion object {
        @Topic.ProjectLevel
        val TOPIC = Topic.create("Aspire AppHost Environment Listener", AppHostEnvironmentListener::class.java)
    }

    /**
     * Publishes the connection metadata of the AppHost identified by [appHostFile].
     */
    fun appHostEnvironmentPublished(appHostFile: Path, environment: AppHostEnvironment)
}
