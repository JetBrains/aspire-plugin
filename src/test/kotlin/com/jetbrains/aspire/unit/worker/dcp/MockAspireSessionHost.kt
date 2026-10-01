package com.jetbrains.aspire.unit.worker.dcp

import com.jetbrains.aspire.sessions.*
import com.jetbrains.aspire.sessions.AspireSessionHost
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel

internal class MockAspireSessionHost : AspireSessionHost {
    override val sessionEvents: ReceiveChannel<SessionEvent>
        field = Channel<SessionEvent>(Channel.UNLIMITED)

    var lastCreateRequest: CreateSessionRequest? = null
        private set

    var lastDeleteRequest: DeleteSessionRequest? = null
        private set

    var onCreate: (CreateSessionRequest) -> CreateSessionResponse = {
        CreateSessionResponse("session-1", null)
    }

    var onDelete: (DeleteSessionRequest) -> DeleteSessionResponse = {
        DeleteSessionResponse(it.sessionId, null)
    }

    override fun createSession(createSessionRequest: CreateSessionRequest): CreateSessionResponse {
        lastCreateRequest = createSessionRequest
        return onCreate(createSessionRequest)
    }

    override fun deleteSession(deleteSessionRequest: DeleteSessionRequest): DeleteSessionResponse {
        lastDeleteRequest = deleteSessionRequest
        return onDelete(deleteSessionRequest)
    }

    suspend fun emit(event: SessionEvent) = sessionEvents.send(event)
}
