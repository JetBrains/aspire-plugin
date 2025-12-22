package com.jetbrains.aspire.worker

import com.intellij.execution.process.ProcessHandler
import com.intellij.util.messages.Topic
import org.jetbrains.annotations.ApiStatus
import java.nio.file.Path

@ApiStatus.Internal
interface AppHostListener {
    companion object {
        @Topic.ProjectLevel
        val TOPIC = Topic.create("Aspire AppHost Listener", AppHostListener::class.java)
    }

    fun appHostStarted(appHostMainFilePath: Path, processHandler: ProcessHandler)
    fun appHostStopped(appHostMainFilePath: Path)
}