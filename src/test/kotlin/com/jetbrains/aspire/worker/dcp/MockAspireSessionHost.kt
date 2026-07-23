package com.jetbrains.aspire.worker.dcp

import com.jetbrains.aspire.generated.*
import com.jetbrains.aspire.sessions.SessionEvent
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel

internal class MockAspireSessionHost : AspireSessionHost {
    private val events = mutableMapOf(DEFAULT_HOST_ID to Channel<SessionEvent>(Channel.UNLIMITED))

    override fun sessionEvents(dcpInstancePrefix: String): ReceiveChannel<SessionEvent>? = events[dcpInstancePrefix]

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

    fun addHost(dcpInstancePrefix: String) {
        events.getOrPut(dcpInstancePrefix) { Channel(Channel.UNLIMITED) }
    }

    suspend fun emit(event: SessionEvent, dcpInstancePrefix: String = DEFAULT_HOST_ID) {
        events.getValue(dcpInstancePrefix).send(event)
    }
}
