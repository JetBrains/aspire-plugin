@file:Suppress("UnstableApiUsage")

package com.jetbrains.aspire.worker.dcp

import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.diagnostic.trace
import com.jetbrains.aspire.generated.*
import com.jetbrains.aspire.sessions.SessionEvent
import com.jetbrains.aspire.worker.dcp.AspireSessionServer.Companion.BEARER_AUTH
import com.jetbrains.aspire.worker.dcp.DcpErrors.AspireSessionNotFound
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.cio.*
import io.ktor.server.engine.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.websocket.*
import io.ktor.websocket.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.selects.select
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.jetbrains.annotations.ApiStatus
import java.security.KeyStore

@ApiStatus.Internal
interface AspireSessionHost {
    val sessionEvents: ReceiveChannel<SessionEvent>

    fun createSession(createSessionRequest: CreateSessionRequest): CreateSessionResponse

    fun deleteSession(deleteSessionRequest: DeleteSessionRequest): DeleteSessionResponse
}

/**
 * TLS material for terminating HTTPS with the ASP.NET dev certificate.
 *
 * @param keyStore a PKCS12 [KeyStore] holding the dev certificate and its private key
 * @param keyAlias the alias of the certificate entry inside [keyStore]
 * @param keyStorePassword password protecting the key store
 * @param privateKeyPassword password protecting the private key entry
 */
@ApiStatus.Internal
class AspireSessionTlsConfig(
    val keyStore: KeyStore,
    val keyAlias: String,
    val keyStorePassword: CharArray,
    val privateKeyPassword: CharArray,
)

/**
 * Configuration for a single embedded server instance.
 *
 * @param port the loopback port to bind (use `0` for an ephemeral port; read [AspireSessionServer.resolvedPort] after start)
 * @param token the Bearer token DCP must present on the `/run_session` endpoints
 * @param tls when non-null, the server terminates TLS instead of serving plain HTTP
 */
@ApiStatus.Internal
data class AspireSessionServerConfig(
    val port: Int,
    val token: String,
    val tls: AspireSessionTlsConfig? = null,
)

/**
 * An embedded Ktor (CIO) server implementing the Aspire DCP "IDE execution" protocol.
 *
 * @param sessionHost the engine session requests are forwarded to
 * @param config the bind port, Bearer token, and optional TLS material
 * @see <a href="https://github.com/dotnet/aspire/blob/main/docs/specs/IDE-execution.md">IDE execution</a>
 */
@ApiStatus.Internal
class AspireSessionServer(
    private val sessionHost: AspireSessionHost,
    private val config: AspireSessionServerConfig,
) {
    companion object {
        private val LOG = logger<AspireSessionServer>()

        private const val LOOPBACK_HOST = "127.0.0.1"

        private const val DCP_INSTANCE_ID_HEADER = "Microsoft-Developer-DCP-Instance-ID"
        private const val DCP_INSTANCE_ID_PREFIX_LENGTH = 5

        private const val BEARER_AUTH = "dcp-bearer"
        private const val BEARER_REALM = "Aspire DCP"

        private val SUPPORTED_PROTOCOL_VERSIONS = listOf("2024-04-23", "2025-10-01")
        private val DEFAULT_SUPPORTED_SESSION_TYPES = listOf("project")
    }

    private val lifecycleMutex = Mutex()
    private var server: EmbeddedServer<CIOApplicationEngine, CIOApplicationEngine.Configuration>? = null

    /** Guards the single-consumer [AspireSessionHost.sessionEvents] channel against double draining. */
    private val notifyMutex = Mutex()
    private var notifyJob: Job? = null

    /** The Bearer token clients must present. Exposed so callers can build the DCP env vars. */
    val token: String get() = config.token

    /** Whether the server terminates TLS (`https`) or serves plain `http`. */
    val isHttps: Boolean get() = config.tls != null

    /** The actually bound port, valid only after [start] has completed. */
    var resolvedPort: Int = config.port
        private set

    /**
     * Starts the server and suspends until its socket is bound and accepting connections, so callers
     * may launch the AppHost process (which connects immediately) only after this returns.
     * Idempotent: a second call while running is a no-op.
     */
    suspend fun start() {
        lifecycleMutex.withLock {
            if (server != null) return

            LOG.trace { "Starting embedded DCP server on $LOOPBACK_HOST:${config.port} (https=$isHttps)" }

            val embedded = buildServer()
            embedded.startSuspend(wait = false)
            resolvedPort = embedded.engine.resolvedConnectors().first().port
            server = embedded

            LOG.trace { "Embedded DCP server bound on $LOOPBACK_HOST:$resolvedPort" }
        }
    }

    /** Stops the server. Idempotent. */
    suspend fun stop() {
        lifecycleMutex.withLock {
            val embedded = server ?: return
            server = null
            LOG.trace { "Stopping embedded DCP server on $LOOPBACK_HOST:$resolvedPort" }
            embedded.stopSuspend(gracePeriodMillis = 500, timeoutMillis = 1000)
        }
    }

    private fun buildServer(): EmbeddedServer<CIOApplicationEngine, CIOApplicationEngine.Configuration> {
        val rootConfig = serverConfig {
            module {
                configureModule()
            }
        }

        return embeddedServer(CIO, rootConfig) {
            val tls = config.tls
            if (tls != null) {
                sslConnector(
                    keyStore = tls.keyStore,
                    keyAlias = tls.keyAlias,
                    keyStorePassword = { tls.keyStorePassword },
                    privateKeyPassword = { tls.privateKeyPassword },
                ) {
                    host = LOOPBACK_HOST
                    port = config.port
                }
            } else {
                connector {
                    host = LOOPBACK_HOST
                    port = config.port
                }
            }
        }
    }

    private fun Application.configureModule() {
        install(Authentication) {
            bearer(BEARER_AUTH) {
                realm = BEARER_REALM
                authenticate { credential ->
                    if (credential.token == config.token) UserIdPrincipal("dcp") else null
                }
            }
        }
        install(ContentNegotiation) {
            json(DcpJson)
        }
        install(WebSockets)

        routing {
            get("/info") {
                call.respond(
                    Info(
                        SUPPORTED_PROTOCOL_VERSIONS,
                        DEFAULT_SUPPORTED_SESSION_TYPES
                    )
                )
            }

            authenticate(BEARER_AUTH) {
                route("/run_session") {
                    install(DcpApiVersionValidation) { supportedVersions = SUPPORTED_PROTOCOL_VERSIONS }

                    put { handleCreateSession(call) }

                    delete("/{sessionId}") { handleDeleteSession(call) }

                    webSocket("/notify") { handleNotify() }
                }
            }
        }
    }

    private suspend fun handleCreateSession(call: ApplicationCall) {
        val aspireHostId = aspireHostId(call) ?: return

        val session = try {
            call.receive<Session>()
        } catch (e: Exception) {
            LOG.trace { "Failed to parse run session request: ${e.message}" }
            call.respond(HttpStatusCode.BadRequest)
            return
        }

        val (createdSessionId, errorCode) = createSession(aspireHostId, session)
        if (createdSessionId != null) {
            call.response.header(HttpHeaders.Location, "/run_session/$createdSessionId")
            call.respond(HttpStatusCode.Created, session)
            return
        }

        val error = errorCode?.let { DcpErrors.fromErrorCode(it) }
        call.respondWithError(error, "Unable to create a session")
    }

    private fun createSession(aspireHostId: String, session: Session): Pair<String?, ErrorCode?> {
        val projectConfig = session.launchConfigurations.filterIsInstance<ProjectLaunchConfiguration>().singleOrNull()
            ?: return null to ErrorCode.UnableToFindSupportedLaunchConfiguration

        val request = CreateProjectSessionRequest(
            projectConfig.projectPath,
            projectConfig.launchProfile,
            projectConfig.disableLaunchProfile == true,
            aspireHostId,
            projectConfig.mode == Mode.Debug,
            session.args?.toTypedArray(),
            mapEnvironmentVariables(session),
        )
        val response = sessionHost.createSession(request)

        return response.sessionId to response.error
    }

    private fun mapEnvironmentVariables(session: Session): Array<SessionEnvironmentVariable>? =
        session.env
            ?.filter { it.value != null }
            ?.map { SessionEnvironmentVariable(it.name, it.value!!) }
            ?.toTypedArray()

    private suspend fun handleDeleteSession(call: ApplicationCall) {
        val aspireHostId = aspireHostId(call) ?: return

        val sessionId = call.parameters["sessionId"]
        if (sessionId == null) {
            LOG.trace { "Unable to find a sessionId" }
            call.respond(HttpStatusCode.BadRequest)
            return
        }

        val (deletedSessionId, errorCode) = sessionHost.deleteSession(DeleteSessionRequest(aspireHostId, sessionId))
        if (deletedSessionId != null) {
            call.respond(HttpStatusCode.OK)
            return
        }

        val error = errorCode?.let { DcpErrors.fromErrorCode(it) }
        call.respondWithError(error, "Unable to delete a session")
    }

    /**
     * Streams session events over the notify WebSocket.
     *
     * The Bearer token is validated by the [BEARER_AUTH] [authenticate] block, and the api-version by
     * [DcpApiVersionValidation], before the HTTP upgrade. An unauthorized client is rejected with a 401
     * and one with an unsupported protocol version with a 400, so neither reaches this handler. DCP
     * always connects with a supported protocol version, so the latter only affects error paths.
     *
     * Delivery is best-effort (at-most-once): [AspireSessionHost.sessionEvents] is the only buffer,
     * so an event already taken from the channel but still in flight inside [send] when the transport
     * dies (or when a reconnecting client displaces this connection) is lost. DCP recovers authoritative
     * state on reconnect, so this is acceptable.
     */
    private suspend fun DefaultWebSocketServerSession.handleNotify() {
        // Take over as the single consumer of the events channel; a previous notify connection (if
        // any) is cancelled so events are never split between two readers.
        val myJob = coroutineContext[Job]
        val previous = notifyMutex.withLock {
            val prev = notifyJob
            notifyJob = myJob
            prev
        }
        previous?.cancelAndJoin()

        try {
            val sender = launch {
                try {
                    for (event in sessionHost.sessionEvents) {
                        val frame = try {
                            SessionEventConverter.convertToFrame(DcpJson, event)
                        } catch (e: Exception) {
                            LOG.warn("Failed to serialize session event; dropping it", e)
                            continue
                        } ?: continue
                        send(Frame.Text(frame))
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    LOG.debug("Notify send loop ended", e)
                }
            }

            val reader = launch {
                for (frame in incoming) {
                    LOG.trace { "Ignoring unexpected inbound notify frame: ${frame.frameType}" }
                }
            }

            // Whichever side finishes first (client close, channel close, or a failed send) wins;
            // then both are torn down before the graceful close below.
            select {
                sender.onJoin {
                    LOG.debug("Notify send loop ended")
                }
                reader.onJoin {
                    LOG.debug("Notify receive loop ended")
                }
            }
            sender.cancelAndJoin()
            reader.cancelAndJoin()
        } finally {
            withContext(NonCancellable) {
                notifyMutex.withLock {
                    if (notifyJob === myJob) notifyJob = null
                }
                runCatching {
                    close(CloseReason(CloseReason.Codes.NORMAL, "Connection closed"))
                }
            }
        }
    }

    private suspend fun aspireHostId(call: ApplicationCall): String? {
        val dcpInstanceId = call.request.header(DCP_INSTANCE_ID_HEADER).orEmpty()
        val hostId = dcpInstanceId.take(DCP_INSTANCE_ID_PREFIX_LENGTH)
        if (hostId.isNotEmpty()) {
            return hostId
        }

        call.respond(HttpStatusCode.BadRequest, ErrorResponse(DcpErrors.InvalidAspireHostId.detail))

        return null
    }

    private suspend fun ApplicationCall.respondWithError(error: DcpError?, unexpectedMessage: String) {
        when {
            error == AspireSessionNotFound -> {
                respond(HttpStatusCode.NoContent)
                return
            }

            error?.kind == DcpErrorKind.CLIENT -> respond(
                HttpStatusCode.BadRequest,
                ErrorResponse(error.detail)
            )

            error?.kind == DcpErrorKind.SERVER -> respond(
                HttpStatusCode.InternalServerError,
                ErrorResponse(error.detail)
            )

            error == null -> respond(
                HttpStatusCode.InternalServerError,
                ErrorResponse(ErrorDetail("UnexpectedError", unexpectedMessage)),
            )
        }
    }
}
