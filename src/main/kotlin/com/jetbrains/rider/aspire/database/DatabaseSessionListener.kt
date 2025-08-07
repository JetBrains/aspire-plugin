package com.jetbrains.rider.aspire.database

import com.intellij.openapi.project.Project
import com.jetbrains.rd.util.lifetime.Lifetime
import com.jetbrains.rider.aspire.sessions.SessionListener
import com.jetbrains.rider.aspire.sessions.SessionManager
import com.jetbrains.rider.aspire.settings.AspireSettings

class DatabaseSessionListener(private val project: Project) : SessionListener {
    companion object {
        private const val CONNECTION_STRINGS = "ConnectionStrings"
        private const val CONNECTION_STRING_PREFIX = "ConnectionStrings__"
    }

    override fun sessionCreated(command: SessionManager.CreateSessionCommand, sessionLifetime: Lifetime) {
        if (!AspireSettings.getInstance().connectToDatabase) return

        val sessionId = command.sessionId
        val connectionStrings = command.createSessionRequest.envs
            ?.filter { it.key.startsWith(CONNECTION_STRINGS) }
            ?: emptyList()

        val service = ResourceDatabaseService.getInstance(project)
        connectionStrings.forEach {
            val connectionName = it.key.substringAfter(CONNECTION_STRING_PREFIX)
            val connectionString = SessionConnectionString(sessionId, connectionName, it.value, sessionLifetime)
            service.put(connectionString)
        }
    }
}