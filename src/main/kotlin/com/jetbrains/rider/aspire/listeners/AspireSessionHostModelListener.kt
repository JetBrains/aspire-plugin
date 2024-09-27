package com.jetbrains.rider.aspire.listeners

import com.intellij.util.messages.Topic
import com.jetbrains.rd.util.lifetime.Lifetime
import com.jetbrains.rider.aspire.generated.AspireSessionHostModel
import java.nio.file.Path

interface AspireSessionHostModelListener {
    companion object {
        @Topic.ProjectLevel
        val TOPIC = Topic("AspireSessionHostModelListener", AspireSessionHostModelListener::class.java)
    }

    fun modelCreated(aspireHostProjectPath: Path, sessionHostModel: AspireSessionHostModel, lifetime: Lifetime)
}