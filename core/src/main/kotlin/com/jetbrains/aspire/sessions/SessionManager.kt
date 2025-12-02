package com.jetbrains.aspire.sessions

import com.intellij.openapi.application.EDT
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.diagnostic.trace
import com.intellij.openapi.project.Project
import com.jetbrains.rd.util.lifetime.LifetimeDefinition
import com.jetbrains.rd.util.lifetime.isAlive
import com.jetbrains.rd.util.put
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import java.util.concurrent.ConcurrentHashMap

/**
 * Service for managing the lifecycle and orchestration of Aspire sessions.
 *
 * This class is responsible for handling requests to create and delete sessions, as well as
 * managing their associated lifetimes and processes. It processes commands submitted to it
 * asynchronously via a command channel.
 */
@Service(Service.Level.PROJECT)
internal class SessionManager(private val project: Project, scope: CoroutineScope) {
    companion object {
        fun getInstance(project: Project) = project.service<SessionManager>()

        private val LOG = logger<SessionManager>()

        /**
         * Time window in milliseconds to batch commands together.
         * Commands received within this window will be grouped and their projects built together.
         */
        private const val BATCH_WINDOW_MS = 1000L
    }

    private val sessionLifetimes = ConcurrentHashMap<String, LifetimeDefinition>()

    private val requests = Channel<SessionRequest>(Channel.UNLIMITED)

    init {
        scope.launch {
            while (true) {
                val batch = mutableListOf<SessionRequest>()

                // Wait for the first command
                val firstCommand = requests.receive()
                batch.add(firstCommand)

                // Collect commands within the batch window
                val batchDeadline = System.currentTimeMillis() + BATCH_WINDOW_MS
                while (true) {
                    val remainingTime = batchDeadline - System.currentTimeMillis()
                    if (remainingTime <= 0) break

                    val command = withTimeoutOrNull(remainingTime) {
                        requests.receive()
                    }

                    if (command != null) {
                        batch.add(command)
                    } else {
                        break
                    }
                }

                LOG.trace { "Processing batch of ${batch.size} command(s)" }
                handleBatchRequests(batch)
            }
        }
    }

    fun submitRequest(request: SessionRequest) {
        requests.trySend(request)
    }

    private suspend fun handleBatchRequests(batch: List<SessionRequest>) {
        val startRequests = batch.filterIsInstance<StartSessionRequest>()
        val stopRequests = batch.filterIsInstance<StopSessionRequest>()

        if (startRequests.isNotEmpty()) {
            handleStartRequests(startRequests)
        }

        if (stopRequests.isNotEmpty()) {
            handleStopRequests(stopRequests)
        }
    }

    private suspend fun handleStartRequests(requests: List<StartSessionRequest>) {
        LOG.trace { "Received ${requests.size} start request(s)" }

        requests.forEach {
            sessionLifetimes.put(it.sessionLifetime.lifetime, it.sessionId, it.sessionLifetime)
        }

        val processLauncher = SessionProcessLauncher.getInstance(project)
        processLauncher.handleStartSessionRequests(requests)
    }

    private suspend fun handleStopRequests(requests: List<StopSessionRequest>) {
        LOG.trace { "Received ${requests.size} stop request(s)" }

        requests.forEach {
            handleStopRequest(it)
        }
    }

    private suspend fun handleStopRequest(request: StopSessionRequest) {
        LOG.info("Stopping session ${request.sessionId}")

        val sessionLifetimeDefinition = sessionLifetimes.remove(request.sessionId)
        if (sessionLifetimeDefinition == null) {
            LOG.warn("Unable to find session ${request.sessionId} lifetime")
            return
        }

        if (sessionLifetimeDefinition.isAlive) {
            withContext(Dispatchers.EDT) {
                LOG.trace { "Terminating session ${request.sessionId} lifetime" }
                sessionLifetimeDefinition.terminate()
            }
        }
    }
}