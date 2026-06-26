package com.jetbrains.aspire.worker.dcp

import com.jetbrains.aspire.generated.MessageLevel
import com.jetbrains.aspire.sessions.SessionEvent
import com.jetbrains.aspire.sessions.SessionLogReceived
import com.jetbrains.aspire.sessions.SessionMessageReceived
import com.jetbrains.aspire.sessions.SessionProcessStarted
import com.jetbrains.aspire.sessions.SessionProcessTerminated
import kotlinx.serialization.json.Json

/**
 * Converts the in-process [SessionEvent]s produced by the session engine into the snake_case
 * notification frames DCP expects on `GET /run_session/notify`.
 *
 * @see <a href="https://github.com/dotnet/aspire/blob/main/docs/specs/IDE-execution.md#common-notification-properties">Common notification properties</a>
 */
internal object SessionEventConverter {
    private const val PROCESS_STARTED_EVENT_NAME = "processRestarted"
    private const val PROCESS_TERMINATED_EVENT_NAME = "sessionTerminated"
    private const val LOG_RECEIVED_EVENT_NAME = "serviceLogs"
    private const val MESSAGE_RECEIVED_EVENT_NAME = "sessionMessage"

    /**
     * Serializes [event] to a JSON text frame, or returns `null` when the event must be dropped.
     */
    fun convertToFrame(json: Json, event: SessionEvent): String? = when (event) {
        is SessionProcessStarted -> json.encodeToString(
            ProcessStartedEvent(
                event.id,
                PROCESS_STARTED_EVENT_NAME,
                event.pid
            )
        )

        is SessionProcessTerminated -> json.encodeToString(
            ProcessTerminatedEvent(
                event.id,
                PROCESS_TERMINATED_EVENT_NAME,
                event.exitCode
            )
        )

        is SessionLogReceived -> {
            val message = modifyText(event.message)
            if (message.isBlank()) {
                null
            } else {
                json.encodeToString(
                    LogReceivedEvent(
                        event.id,
                        LOG_RECEIVED_EVENT_NAME,
                        event.isStdErr,
                        message
                    )
                )
            }
        }

        is SessionMessageReceived -> {
            val level = when (event.level) {
                MessageLevel.Error -> "error"
                MessageLevel.Info -> "info"
                MessageLevel.Debug -> "debug"
            }
            val errorDetail = event.errorCode?.let { DcpErrors.fromErrorCode(it).detail }

            json.encodeToString(
                MessageReceivedEvent(
                    sessionId = event.id,
                    notificationType = MESSAGE_RECEIVED_EVENT_NAME,
                    level = level,
                    message = event.message,
                    code = errorDetail?.code,
                    details = errorDetail?.let { listOf(it) },
                ),
            )
        }
    }

    private fun modifyText(text: String): String {
        var modified = text

        modified = when {
            modified.startsWith("\r\n") -> modified.substring(2)
            modified.startsWith("\n") -> modified.substring(1)
            else -> modified
        }

        modified = when {
            modified.endsWith("\r\n") -> modified.substring(0, modified.length - 2)
            modified.endsWith("\n") -> modified.substring(0, modified.length - 1)
            else -> modified
        }

        return modified
    }
}
