package com.jetbrains.aspire.worker

import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.diagnostic.trace
import com.jetbrains.aspire.generated.*
import com.jetbrains.aspire.sessions.*
import com.jetbrains.rd.util.threading.coroutines.asCoroutineDispatcher
import com.jetbrains.rdclient.protocol.RdDispatcher
import kotlinx.coroutines.withContext

/**
 * Handles session events by dispatching them to the RD model signals.
 *
 * All session events ([SessionProcessStarted], [SessionProcessTerminated],
 * [SessionLogReceived], [SessionMessageReceived]) follow the same pattern:
 * log the event, switch to the RD dispatcher, and fire the corresponding signal.
 */
internal class SessionEventHandler {
    companion object {
        private val LOG = logger<SessionEventHandler>()
    }

    suspend fun handleSessionEvent(
        sessionEvent: SessionEvent,
        appHostModel: AspireHostModel,
        dispatcher: RdDispatcher
    ) {
        when (sessionEvent) {
            is SessionProcessStarted -> handleProcessStartedEvent(sessionEvent, appHostModel, dispatcher)
            is SessionProcessTerminated -> handleProcessTerminatedEvent(sessionEvent, appHostModel, dispatcher)
            is SessionLogReceived -> handleSessionLogReceivedEvent(sessionEvent, appHostModel, dispatcher)
            is SessionMessageReceived -> handleSessionMessageReceivedEvent(sessionEvent, appHostModel, dispatcher)
        }
    }

    private suspend fun handleProcessStartedEvent(
        event: SessionProcessStarted,
        appHostModel: AspireHostModel,
        dispatcher: RdDispatcher
    ) {
        LOG.trace { "Aspire session started (${event.id}, ${event.pid})" }
        withContext(dispatcher.asCoroutineDispatcher) {
            appHostModel.processStarted.fire(ProcessStarted(event.id, event.pid))
        }
    }

    private suspend fun handleProcessTerminatedEvent(
        event: SessionProcessTerminated,
        appHostModel: AspireHostModel,
        dispatcher: RdDispatcher
    ) {
        LOG.trace { "Aspire session terminated (${event.id}, ${event.exitCode})" }
        withContext(dispatcher.asCoroutineDispatcher) {
            appHostModel.processTerminated.fire(ProcessTerminated(event.id, event.exitCode))
        }
    }

    private suspend fun handleSessionLogReceivedEvent(
        event: SessionLogReceived,
        appHostModel: AspireHostModel,
        dispatcher: RdDispatcher
    ) {
        LOG.trace { "Aspire session log received (${event.id}, ${event.isStdErr}, ${event.message})" }
        withContext(dispatcher.asCoroutineDispatcher) {
            appHostModel.logReceived.fire(LogReceived(event.id, event.isStdErr, event.message))
        }
    }

    private suspend fun handleSessionMessageReceivedEvent(
        event: SessionMessageReceived,
        appHostModel: AspireHostModel,
        dispatcher: RdDispatcher
    ) {
        LOG.trace { "Aspire session message received (${event.id}, ${event.level}, ${event.message})" }
        withContext(dispatcher.asCoroutineDispatcher) {
            appHostModel.messageReceived.fire(
                MessageReceived(event.id, event.level, event.message, event.errorCode)
            )
        }
    }
}
