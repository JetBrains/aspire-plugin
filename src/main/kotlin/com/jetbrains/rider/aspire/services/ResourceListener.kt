package com.jetbrains.rider.aspire.services

import com.intellij.util.messages.Topic
import com.jetbrains.rider.aspire.services.a.AspireResource

interface ResourceListener {
    companion object {
        @Topic.ProjectLevel
        val TOPIC = Topic.create("Aspire Resource Listener", ResourceListener::class.java)
    }

    fun resourceCreated(resource: AspireResource)
    fun resourceUpdated(resource: AspireResource)
}