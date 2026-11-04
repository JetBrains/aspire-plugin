package com.jetbrains.aspire.rider.sessions

import com.intellij.execution.process.ProcessEvent
import com.intellij.execution.process.ProcessHandler
import com.intellij.execution.process.ProcessListener
import com.intellij.execution.process.ProcessOutputType
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.diagnostic.trace
import com.intellij.openapi.rd.createNestedDisposable
import com.intellij.openapi.util.Key
import com.jetbrains.aspire.sessions.SessionEvent
import com.jetbrains.aspire.sessions.SessionLogReceived
import com.jetbrains.aspire.sessions.SessionProcessStarted
import com.jetbrains.aspire.sessions.SessionProcessTerminated
import com.jetbrains.rd.util.lifetime.Lifetime
import com.jetbrains.rd.util.lifetime.isAlive
import com.jetbrains.rd.util.threading.coroutines.launch
import com.jetbrains.rider.debugger.DebuggerWorkerProcessHandler
import com.jetbrains.rider.debugger.DebuggerWorkerProcessHandlerBase
import com.jetbrains.rider.run.pid
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import org.jetbrains.annotations.ApiStatus
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.time.Duration.Companion.milliseconds

/**
 * Process listener for an Aspire session process.
 *
 * Rider may bind this listener to the session lifetime and remove it when that lifetime ends,
 * which can happen before the process actually exits (e.g., on DCP `DELETE session` or AppHost disposal).
 * To not lose the final output and the termination event, this listener handles only the process start
 * and registers a separate [ProcessEventHandler] bound to its own lifetime (nested in `parentLifetime`).
 * The handler is the only place where the process output and termination are processed.
 */
@ApiStatus.Internal
class SessionProcessEventListener(
    private val sessionId: String,
    private val sessionEvents: Channel<SessionEvent>,
    parentLifetime: Lifetime,
    private val terminateProcessLifetime: () -> Unit
) : ProcessListener {
    companion object {
        private val LOG = logger<SessionProcessEventListener>()
        private const val UNSUCCESSFUL_EXIT_CODE = -1
        private const val UNKNOWN_EXIT_CODE = 0
    }

    private val listenerLifetimeDefinition by lazy { parentLifetime.createNested() }

    private val stdOutBuffer by lazy {
        SessionLogBuffer(sessionId, false, sessionEvents, listenerLifetimeDefinition.lifetime)
    }
    private val stdErrBuffer by lazy {
        SessionLogBuffer(sessionId, true, sessionEvents, listenerLifetimeDefinition.lifetime)
    }

    private val eventHandler = ProcessEventHandler()

    private val isStarted = AtomicBoolean(false)
    private val isTerminated = AtomicBoolean(false)

    override fun startNotified(event: ProcessEvent) {
        if (!isStarted.compareAndSet(false, true)) {
            LOG.trace { "Session $sessionId process start was already handled" }
            return
        }

        LOG.info("Session $sessionId process was started")
        val processHandler = event.processHandler

        processHandler.addProcessListener(
            eventHandler,
            listenerLifetimeDefinition.lifetime.createNestedDisposable()
        )

        if (processHandler is DebuggerWorkerProcessHandlerBase) {
            processHandler.workerModel.targetExited.advise(listenerLifetimeDefinition.lifetime) {
                targetProcessTerminated(it.exitCode)
            }
        }

        val pid = when (processHandler) {
            is DebuggerWorkerProcessHandler -> processHandler.debuggerWorkerRealHandler.pid()
            is ProcessHandler -> processHandler.pid()
            else -> null
        }
        if (pid == null) {
            LOG.warn("Unable to determine process id for the session $sessionId")
            terminateSession(UNSUCCESSFUL_EXIT_CODE)
        } else {
            LOG.trace { "Session $sessionId process id = $pid" }
            val eventSendingResult = sessionEvents.trySend(SessionProcessStarted(sessionId, pid))
            if (!eventSendingResult.isSuccess) {
                LOG.warn("Unable to send an event for session $sessionId start")
            }
        }
    }

    override fun processNotStarted() {
        LOG.warn("Session $sessionId process is not started")
        terminateSession(UNSUCCESSFUL_EXIT_CODE)
    }

    private fun targetProcessTerminated(exitCode: Int) {
        LOG.info("Session $sessionId target process was terminated with exitCode $exitCode")
        terminateSession(exitCode)
    }

    private fun terminateSession(exitCode: Int) {
        if (!isTerminated.compareAndSet(false, true)) {
            LOG.trace { "Session $sessionId was already terminated" }
            return
        }

        LOG.trace { "Terminating session $sessionId with exitCode $exitCode" }
        stdOutBuffer.flush()
        stdErrBuffer.flush()

        val eventSendingResult = sessionEvents.trySend(SessionProcessTerminated(sessionId, exitCode))
        if (!eventSendingResult.isSuccess) {
            LOG.warn("Unable to send an event for session $sessionId termination")
        }

        listenerLifetimeDefinition.terminate()
        terminateProcessLifetime()
    }

    /**
     * Handles the process output and termination.
     * It's the only place where these events are processed, so they are never handled twice.
     */
    private inner class ProcessEventHandler : ProcessListener {
        override fun onTextAvailable(event: ProcessEvent, outputType: Key<*>) {
            if (outputType == ProcessOutputType.STDERR) {
                stdErrBuffer.append(event.text)
            } else {
                stdOutBuffer.append(event.text)
            }
        }

        override fun processTerminated(event: ProcessEvent) {
            LOG.info("Session $sessionId process was terminated (${event.exitCode}, ${event.text})")
            val isDebuggerWorkerProcess = event.processHandler is DebuggerWorkerProcessHandlerBase
            val exitCode = if (!isDebuggerWorkerProcess) event.exitCode else UNKNOWN_EXIT_CODE
            terminateSession(exitCode)
        }
    }

    private class SessionLogBuffer(
        private val sessionId: String,
        private val isStdErr: Boolean,
        private val sessionEvents: Channel<SessionEvent>,
        private val lifetime: Lifetime
    ) {
        companion object {
            private val FLUSH_INTERVAL_MS = 100L.milliseconds
            private const val BUFFER_SIZE_LIMIT = 8192
        }

        init {
            lifetime.launch {
                while (lifetime.isAlive) {
                    delay(FLUSH_INTERVAL_MS)
                    if (isFlushedOnLimit.compareAndSet(true, false)) {
                        continue // we've flushed the buffer, no need to do it again
                    }
                    flush()
                }
            }
        }

        private val buffer = StringBuilder()
        private val lock = Any()
        private val isFlushedOnLimit: AtomicBoolean = AtomicBoolean(false)

        fun append(text: String) {
            synchronized(lock) {
                buffer.append(text)
                if (buffer.length >= BUFFER_SIZE_LIMIT) {
                    isFlushedOnLimit.set(true)
                    LOG.trace { "Session $sessionId log buffer size limit reached, flushing" }
                    flush()
                }
            }
        }

        fun flush() {
            var toSend: String? = null
            synchronized(lock) {
                if (buffer.isNotEmpty()) {
                    toSend = buffer.toString()
                    buffer.setLength(0)
                }
            }
            toSend?.let { send(it) }
        }

        private fun send(text: String) {
            val result = sessionEvents.trySend(SessionLogReceived(sessionId, isStdErr, text))
            if (!result.isSuccess) {
                LOG.warn("Unable to send an event for session $sessionId log")
            }
        }
    }
}
