package com.jetbrains.aspire.dashboard

import com.intellij.util.messages.Topic
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
interface ResourceListener {
    companion object {
        @Topic.ProjectLevel
        val TOPIC = Topic.create("Aspire Resource Listener", ResourceListener::class.java)
    }

    fun resourceCreated(resource: AspireResource)
    fun resourceUpdated(resource: AspireResource)
}