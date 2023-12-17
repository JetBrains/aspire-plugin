package me.rafaelldi.aspire.sessionHost

import com.intellij.util.messages.Topic
import com.jetbrains.rd.util.lifetime.Lifetime
import me.rafaelldi.aspire.generated.AspireSessionHostModel

interface AspireHostLifecycleListener {
    companion object {
        @Topic.ProjectLevel
        val TOPIC = Topic.create("Host Lifecycle Listener", AspireHostLifecycleListener::class.java)
    }

    fun hostStarted(hostConfig: AspireHostConfig, hostModel: AspireSessionHostModel, hostLifetime: Lifetime)
}