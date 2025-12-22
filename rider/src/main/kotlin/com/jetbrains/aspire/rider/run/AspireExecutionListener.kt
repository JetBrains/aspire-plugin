package com.jetbrains.aspire.rider.run

import com.intellij.execution.ExecutionListener
import com.intellij.execution.process.ProcessHandler
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.openapi.project.Project
import com.jetbrains.aspire.run.AspireRunConfiguration
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
        val mainFilePath = Path(profile.parameters.mainFilePath)
        project.messageBus
            .syncPublisher(AppHostListener.TOPIC)
            .appHostStarted(mainFilePath, handler)
    }

    override fun processTerminated(
        executorId: String,
        env: ExecutionEnvironment,
        handler: ProcessHandler,
        exitCode: Int
    ) {
        val profile = env.runProfile
        if (profile !is AspireRunConfiguration) return
        val mainFilePath = Path(profile.parameters.mainFilePath)
        project.messageBus
            .syncPublisher(AppHostListener.TOPIC)
            .appHostStopped(mainFilePath)
    }
}