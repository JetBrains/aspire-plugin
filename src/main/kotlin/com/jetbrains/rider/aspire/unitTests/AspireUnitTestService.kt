package com.jetbrains.rider.aspire.unitTests

import com.intellij.openapi.application.EDT
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.jetbrains.rd.framework.*
import com.jetbrains.rd.framework.impl.RdTask
import com.jetbrains.rd.platform.util.idea.LifetimedService
import com.jetbrains.rd.protocol.IdeRootMarshallersProvider
import com.jetbrains.rd.util.lifetime.Lifetime
import com.jetbrains.rd.util.lifetime.LifetimeDefinition
import com.jetbrains.rd.util.threading.coroutines.lifetimedCoroutineScope
import com.jetbrains.rdclient.protocol.RdDispatcher
import com.jetbrains.rider.aspire.generated.*
import com.jetbrains.rider.aspire.run.AspireHostConfig
import com.jetbrains.rider.aspire.sessionHost.SessionHostManager
import com.jetbrains.rider.aspire.util.DCP_INSTANCE_ID_PREFIX
import com.jetbrains.rider.aspire.util.DEBUG_SESSION_PORT
import com.jetbrains.rider.aspire.util.DEBUG_SESSION_TOKEN
import com.jetbrains.rider.util.NetUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import kotlin.io.path.Path
import kotlin.io.path.absolutePathString
import kotlin.io.path.nameWithoutExtension

@Service(Service.Level.PROJECT)
class AspireUnitTestService(private val project: Project, private val scope: CoroutineScope) : LifetimedService() {
    companion object {
        fun getInstance(project: Project): AspireUnitTestService = project.service()
        private val LOG = logger<AspireUnitTestService>()
    }

    private val sessionHosts = ConcurrentHashMap<String, SessionHostForUnitTestRun>()

    fun startSessionHost(
        lifetime: Lifetime,
        request: StartSessionHostRequest,
        rdTask: RdTask<StartSessionHostResponse>
    ) {
        val existingSessionHost = sessionHosts[request.unitTestRunId]
        if (existingSessionHost != null) {
            val response = StartSessionHostResponse(existingSessionHost.environmentVariables.toTypedArray())
            rdTask.set(response)
            return
        }

        scope.launch(Dispatchers.Default) {
            lifetimedCoroutineScope(lifetime) {
                LOG.trace("Starting a session host for a unit test session")
                val aspireHostLifetimeDefinition = serviceLifetime.createNested()

                val aspireHostProjectPath = Path(request.aspireHostProjectPath)
                val dcpInstancePrefix = UUID.randomUUID().toString()
                val debugSessionToken = UUID.randomUUID().toString()
                val debugSessionPort = NetUtils.findFreePort(47100)
                val debugSessionUrl = "localhost:$debugSessionPort"

                val config = AspireHostConfig(
                    aspireHostProjectPath.nameWithoutExtension,
                    dcpInstancePrefix,
                    debugSessionToken,
                    debugSessionPort,
                    request.underDebugger,
                    null,
                    null,
                    aspireHostLifetimeDefinition.lifetime,
                    aspireHostProjectPath,
                    null,
                    null
                )

                val environmentVariables = listOf(
                    SessionHostEnvironmentVariable(
                        DEBUG_SESSION_TOKEN,
                        debugSessionToken
                    ),
                    SessionHostEnvironmentVariable(
                        DEBUG_SESSION_PORT,
                        debugSessionUrl
                    ),
                    SessionHostEnvironmentVariable(
                        DCP_INSTANCE_ID_PREFIX,
                        dcpInstancePrefix
                    )
                )

                val newSessionHost = SessionHostForUnitTestRun(
                    aspireHostLifetimeDefinition,
                    environmentVariables
                )
                val currentSessionHost = sessionHosts.putIfAbsent(request.unitTestRunId, newSessionHost)

                if (currentSessionHost == null) {
                    startProtocolAndSubscribe(config)
                    val response = StartSessionHostResponse(environmentVariables.toTypedArray())
                    rdTask.set(response)
                } else {
                    val response = StartSessionHostResponse(currentSessionHost.environmentVariables.toTypedArray())
                    rdTask.set(response)
                }
            }
        }
    }

    private suspend fun startProtocolAndSubscribe(config: AspireHostConfig) = withContext(Dispatchers.EDT) {
        val protocol = startProtocol(config.aspireHostLifetime)
        val sessionHostModel = protocol.aspireSessionHostModel

        val aspireHostConfig = AspireHostModelConfig(
            config.id,
            config.aspireHostProjectPath.absolutePathString(),
            config.resourceServiceEndpointUrl,
            config.resourceServiceApiKey
        )
        val aspireHostModel = AspireHostModel(aspireHostConfig)

        sessionHostModel.aspireHosts.put(config.id, aspireHostModel)

        SessionHostManager
            .getInstance(project)
            .startSessionHost(config, protocol.wire.serverPort, aspireHostModel, sessionHostModel)
    }

    private suspend fun startProtocol(lifetime: Lifetime) = withContext(Dispatchers.EDT) {
        val dispatcher = RdDispatcher(lifetime)
        val wire = SocketWire.Server(lifetime, dispatcher, null)
        val protocol = Protocol(
            "AspireSessionHost::protocol",
            Serializers(IdeRootMarshallersProvider),
            Identities(IdKind.Server),
            dispatcher,
            wire,
            lifetime
        )
        return@withContext protocol
    }

    fun stopSessionHost(request: StopSessionHostRequest, rdTask: RdTask<Unit>) {
        val sessionHost = sessionHosts.remove(request.unitTestRunId)
        sessionHost?.lifetimeDefinition?.terminate()
        rdTask.set(Unit)
    }

    fun stopSessionHost(unitTestRunId: String) {
        val sessionHost = sessionHosts.remove(unitTestRunId)
        sessionHost?.lifetimeDefinition?.terminate()
    }

    private data class SessionHostForUnitTestRun(
        val lifetimeDefinition: LifetimeDefinition,
        val environmentVariables: List<SessionHostEnvironmentVariable>
    )
}