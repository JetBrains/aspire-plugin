package com.jetbrains.aspire.worker.dcp

import com.jetbrains.aspire.generated.*
import com.jetbrains.aspire.sessions.SessionEvent
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel

internal class MockAspireSessionHost : AspireSessionHost {
    private val events = Channel<SessionEvent>(Channel.UNLIMITED)
    override val sessionEvents: ReceiveChannel<SessionEvent> get() = events

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

    suspend fun emit(event: SessionEvent) = events.send(event)
}
