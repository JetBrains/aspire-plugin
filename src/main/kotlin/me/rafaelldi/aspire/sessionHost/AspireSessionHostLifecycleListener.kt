package me.rafaelldi.aspire.sessionHost

import com.intellij.util.messages.Topic
import com.jetbrains.rd.util.lifetime.Lifetime
import me.rafaelldi.aspire.generated.AspireSessionHostModel

interface AspireSessionHostLifecycleListener {
    companion object {
        @Topic.ProjectLevel
        val TOPIC = Topic.create("Host Lifecycle Listener", AspireSessionHostLifecycleListener::class.java)
    }

    fun sessionHostStarted(hostConfig: AspireSessionHostConfig, hostModel: AspireSessionHostModel, hostLifetime: Lifetime)
}