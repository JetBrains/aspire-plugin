package me.rafaelldi.aspire.sessionHost

import me.rafaelldi.aspire.generated.AspireSessionHostModel
import me.rafaelldi.aspire.generated.aspireSessionHostModel
import com.intellij.execution.CantRunException
import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.process.KillableColoredProcessHandler
import com.intellij.execution.process.ProcessEvent
import com.intellij.execution.process.ProcessListener
import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.project.Project
import com.intellij.openapi.rd.createNestedDisposable
import com.intellij.openapi.rd.util.withUiContext
import com.jetbrains.rd.framework.*
import com.jetbrains.rd.util.addUnique
import com.jetbrains.rd.util.lifetime.Lifetime
import com.jetbrains.rd.util.lifetime.isNotAlive
import com.jetbrains.rdclient.protocol.RdDispatcher
import com.jetbrains.rider.runtime.RiderDotNetActiveRuntimeHost
import java.nio.charset.StandardCharsets
import java.nio.file.Path
import java.util.concurrent.ConcurrentHashMap
import kotlin.io.path.div

@Service
class AspireSessionHostService {
    companion object {
        fun getInstance(): AspireSessionHostService = service()

        private val LOG = logger<AspireSessionHostService>()

        private const val ASPNETCORE_URLS = "ASPNETCORE_URLS"
        private const val RIDER_RD_PORT = "RIDER_RD_PORT"
    }

    private val pluginId = PluginId.getId("me.rafaelldi.aspire")

    private val hostAssemblyPath: Path = run {
        val plugin = PluginManagerCore.getPlugin(pluginId) ?: error("Plugin $pluginId could not be found.")
        val basePath = plugin.pluginPath ?: error("Could not detect path of plugin $plugin on disk.")
        basePath / "aspire-session-host" / "aspire-session-host.dll"
    }

    private val hosts = ConcurrentHashMap<String, HostConfiguration>()

    data class HostConfiguration(
        val id: String,
        val projectName: String,
        val isDebug: Boolean,
        val aspNetPort: Int
    )

    fun getHost(id: String) = hosts[id]

    suspend fun startHost(project: Project, hostConfig: HostConfiguration, lifetime: Lifetime) {
        LOG.info("Starting Aspire session host: $hostConfig")

        if (lifetime.isNotAlive) {
            LOG.warn("Unable to start Aspire host because lifetime is not alive")
            return
        }

        val dotnet = RiderDotNetActiveRuntimeHost.getInstance(project).dotNetCoreRuntime.value
            ?: throw CantRunException("Cannot find active .NET runtime")

        if (hosts.containsKey(hostConfig.id))
            throw CantRunException("Session id is not unique")

        val hostLifetime = lifetime.createNested()

        hosts.addUnique(hostLifetime, hostConfig.id, hostConfig)

        val protocol = startProtocol(hostLifetime)
        subscribe(hostConfig.id, protocol.aspireSessionHostModel, hostLifetime, project)

        val commandLine = GeneralCommandLine()
            .withExePath(dotnet.cliExePath)
            .withCharset(StandardCharsets.UTF_8)
            .withParameters(hostAssemblyPath.toString())
            .withEnvironment(
                mapOf(
                    ASPNETCORE_URLS to "http://localhost:${hostConfig.aspNetPort}/",
                    RIDER_RD_PORT to "${protocol.wire.serverPort}"
                )
            )
        val processHandler = KillableColoredProcessHandler.Silent(commandLine)
        hostLifetime.onTermination {
            if (!processHandler.isProcessTerminating && !processHandler.isProcessTerminated) {
                processHandler.killProcess()
            }
        }
        processHandler.addProcessListener(object : ProcessListener {
            override fun processTerminated(event: ProcessEvent) {
                hostLifetime.executeIfAlive {
                    hostLifetime.terminate(true)
                }
            }
        }, hostLifetime.createNestedDisposable())
        processHandler.startNotify()
    }

    private suspend fun startProtocol(lifetime: Lifetime) = withUiContext {
        val dispatcher = RdDispatcher(lifetime)
        val wire = SocketWire.Server(lifetime, dispatcher, null)
        val protocol = Protocol(
            "AspireSessionHost::protocol",
            Serializers(),
            Identities(IdKind.Server),
            dispatcher,
            wire,
            lifetime
        )
        return@withUiContext protocol
    }

    private suspend fun subscribe(
        hostId: String,
        hostModel: AspireSessionHostModel,
        hostLifetime: Lifetime,
        project: Project
    ) = withUiContext {
        hostModel.sessions.view(hostLifetime) { sessionLifetime, sessionId, sessionModel ->
            LOG.info("New session added $sessionId, $sessionModel")
            val runner = AspireSessionRunner.getInstance(project)
            runner.runSession(
                AspireSessionRunner.RunSessionCommand(
                    sessionId,
                    sessionModel,
                    sessionLifetime,
                    hostId,
                    hostModel,
                    hostLifetime
                )
            )
        }
    }
}