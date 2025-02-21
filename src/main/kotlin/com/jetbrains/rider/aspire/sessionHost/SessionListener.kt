package com.jetbrains.rider.aspire.sessionHost

import com.intellij.util.messages.Topic
import com.jetbrains.rd.util.lifetime.Lifetime

interface SessionListener {
    companion object {
        @Topic.ProjectLevel
        val TOPIC = Topic.create("Aspire Session Listener", SessionListener::class.java)
    }

    fun sessionCreated(command: SessionManager.CreateSessionCommand, sessionLifetime: Lifetime)
}