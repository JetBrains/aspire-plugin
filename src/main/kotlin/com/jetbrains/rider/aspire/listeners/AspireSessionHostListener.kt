package com.jetbrains.rider.aspire.listeners

import com.intellij.util.messages.Topic
import com.jetbrains.rd.util.lifetime.Lifetime
import com.jetbrains.rider.aspire.generated.AspireSessionHostModel
import com.jetbrains.rider.aspire.run.AspireHostConfig
import java.nio.file.Path

interface AspireSessionHostListener {
    companion object {
        @Topic.ProjectLevel
        val TOPIC = Topic("AspireSessionHostListener", AspireSessionHostListener::class.java)
    }

    fun configCreated(aspireHostProjectPath: Path, config: AspireHostConfig)
    fun modelCreated(aspireHostProjectPath: Path, sessionHostModel: AspireSessionHostModel, lifetime: Lifetime)
}