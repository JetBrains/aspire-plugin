package com.jetbrains.aspire.rider.run

import com.intellij.execution.ExecutionListener
import com.intellij.execution.process.ProcessHandler
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.openapi.project.Project
import com.jetbrains.aspire.worker.AppHostListener
import kotlin.io.path.Path

internal class AspireExecutionListener(private val project: Project) : ExecutionListener {
    override fun processStarted(
        executorId: String,
        env: ExecutionEnvironment,
        handler: ProcessHandler
    ) {
        val profile = env.runProfile
        if (profile !is AspireRunConfiguration) return

        val appHostFilePath = Path(profile.parameters.appHostFilePath)
        val runConfigName = profile.name

        project.messageBus
            .syncPublisher(AppHostListener.TOPIC)
            .appHostStarted(appHostFilePath, runConfigName, handler)
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