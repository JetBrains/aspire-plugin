package com.jetbrains.aspire.worker

import com.intellij.util.messages.Topic
import org.jetbrains.annotations.ApiStatus
import java.nio.file.Path

@ApiStatus.Internal
interface AppHostDetectionListener {
    companion object {
        @Topic.ProjectLevel
        val TOPIC = Topic.create("Aspire AppHost Detection Listener", AppHostDetectionListener::class.java)
    }

    fun appHostDetected(appHostName: String, appHostMainFilePath: Path)
    fun appHostRemoved(appHostMainFilePath: Path)
}