package com.intellij.aspire.sessionHost

import com.intellij.aspire.generated.AspireSessionHostModel
import com.intellij.aspire.generated.aspireSessionHostModel
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
import com.jetbrains.rd.util.lifetime.Lifetime
import com.jetbrains.rdclient.protocol.RdDispatcher
import com.jetbrains.rider.runtime.RiderDotNetActiveRuntimeHost
import java.nio.charset.StandardCharsets
import java.nio.file.Path
import kotlin.io.path.div

@Service
class AspireSessionHost {
    companion object {
        fun getInstance(): AspireSessionHost = service()

        private val LOG = logger<AspireSessionHost>()

        private const val RIDER_RD_PORT = "RIDER_RD_PORT"
    }

    private val pluginId = PluginId.getId("com.github.rafaelldi.aspireplugin")

    private val hostAssemblyPath: Path = run {
        val plugin = PluginManagerCore.getPlugin(pluginId) ?: error("Plugin $pluginId could not be found.")
        val basePath = plugin.pluginPath ?: error("Could not detect path of plugin $plugin on disk.")
        basePath / "aspire-session-host" / "aspire-session-host.dll"
    }

    suspend fun start(project: Project, aspNetPort: Int, lifetime: Lifetime) {
        val dotnet = RiderDotNetActiveRuntimeHost.getInstance(project).dotNetCoreRuntime.value
            ?: throw CantRunException("Cannot find active .NET runtime.")

        val processLifetime = lifetime.createNested()

        val protocol = startProtocol(processLifetime)
        subscribe(protocol.aspireSessionHostModel, processLifetime, project)

        val commandLine = GeneralCommandLine()
            .withExePath(dotnet.cliExePath)
            .withCharset(StandardCharsets.UTF_8)
            .withParameters(hostAssemblyPath.toString())
            .withEnvironment(
                mapOf(
                    "ASPNETCORE_URLS" to "http://localhost:$aspNetPort/",
                    RIDER_RD_PORT to "${protocol.wire.serverPort}"
                )
            )
        val processHandler = KillableColoredProcessHandler.Silent(commandLine)
        processLifetime.onTermination {
            if (!processHandler.isProcessTerminating && !processHandler.isProcessTerminated) {
                processHandler.killProcess()
            }
        }
        processHandler.addProcessListener(object : ProcessListener {
            override fun processTerminated(event: ProcessEvent) {
                processLifetime.executeIfAlive {
                    processLifetime.terminate(true)
                }
            }
        }, processLifetime.createNestedDisposable())
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
        model: AspireSessionHostModel,
        lifetime: Lifetime,
        project: Project
    ) = withUiContext {
        model.sessions.view(lifetime) { lt, id, session ->
            LOG.info("New session added $id, $session")

            val runner = AspireSessionRunner.getInstance(project)
            runner.runSession(session)

            lt.onTermination {
                LOG.info("Session removed $id")
                runner.stopSession(session)
            }
        }
    }
}