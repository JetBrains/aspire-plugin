package me.rafaelldi.aspire.sessionHost

import com.intellij.execution.CantRunException
import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.process.KillableColoredProcessHandler
import com.intellij.execution.process.ProcessEvent
import com.intellij.execution.process.ProcessListener
import com.intellij.execution.process.ProcessOutputType
import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.project.Project
import com.intellij.openapi.rd.createNestedDisposable
import com.intellij.openapi.util.Key
import com.jetbrains.rd.util.lifetime.LifetimeDefinition
import com.jetbrains.rider.runtime.RiderDotNetActiveRuntimeHost
import com.jetbrains.rider.runtime.dotNetCore.DotNetCoreRuntime
import me.rafaelldi.aspire.run.AspireHostConfig
import me.rafaelldi.aspire.util.decodeAnsiCommandsToString
import java.nio.charset.StandardCharsets
import java.nio.file.Path
import kotlin.io.path.div

@Service(Service.Level.PROJECT)
class SessionHostLauncher(private val project: Project) {
    companion object {
        fun getInstance(project: Project) = project.service<SessionHostLauncher>()

        private val LOG = logger<SessionHostLauncher>()

        private const val RIDER_PARENT_PROCESS_ID = "RIDER_PARENT_PROCESS_ID"
        private const val RIDER_RD_PORT = "Rider_Connection__RdPort"
        private const val RIDER_RESOURCE_SERVICE_ENDPOINT_URL = "Rider_ResourceService__EndpointUrl"
        private const val RIDER_RESOURCE_SERVICE_API_KEY = "Rider_ResourceService__ApiKey"
    }

    private val pluginId = PluginId.getId("me.rafaelldi.aspire")

    private val hostAssemblyPath: Path = run {
        val plugin = PluginManagerCore.getPlugin(pluginId) ?: error("Plugin $pluginId could not be found.")
        val basePath = plugin.pluginPath ?: error("Could not detect path of plugin $plugin on disk.")
        basePath / "aspire-session-host" / "aspire-session-host.dll"
    }

    fun launchSessionHost(
        aspireHostConfig: AspireHostConfig,
        sessionHostRdPort: Int,
        aspireHostLifetime: LifetimeDefinition
    ) {
        LOG.info("Starting Aspire session host")

        val dotnet = RiderDotNetActiveRuntimeHost.getInstance(project).dotNetCoreRuntime.value
            ?: throw CantRunException("Cannot find active .NET runtime")

        val commandLine = getCommandLine(dotnet, aspireHostConfig, sessionHostRdPort)
        LOG.trace("Host command line: ${commandLine.commandLineString}")

        val processHandler = KillableColoredProcessHandler(commandLine)
        aspireHostLifetime.onTermination {
            if (!processHandler.isProcessTerminating && !processHandler.isProcessTerminated) {
                LOG.trace("Killing Aspire session host process")
                processHandler.killProcess()
            }
        }
        processHandler.addProcessListener(object : ProcessListener {
            override fun onTextAvailable(event: ProcessEvent, outputType: Key<*>) {
                val text = decodeAnsiCommandsToString(event.text, outputType)
                if (outputType == ProcessOutputType.STDERR) {
                    LOG.error(text)
                } else {
                    LOG.debug(text)
                }
            }

            override fun processTerminated(event: ProcessEvent) {
                aspireHostLifetime.executeIfAlive {
                    LOG.trace("Terminating Aspire session host lifetime")
                    aspireHostLifetime.terminate(true)
                }
            }
        }, aspireHostLifetime.createNestedDisposable())

        processHandler.startNotify()
        LOG.trace("Aspire session host started")
    }

    private fun getCommandLine(
        dotnet: DotNetCoreRuntime,
        aspireHostConfig: AspireHostConfig,
        rdPort: Int
    ): GeneralCommandLine {
        val commandLine = GeneralCommandLine()
            .withExePath(dotnet.cliExePath)
            .withCharset(StandardCharsets.UTF_8)
            .withParameters(hostAssemblyPath.toString())
            .withWorkDirectory(hostAssemblyPath.parent.toFile())
            .withEnvironment(
                buildMap {
                    put("Kestrel__Endpoints__Http__Url", "http://localhost:${aspireHostConfig.debugSessionPort}/")
                    put(RIDER_RD_PORT, "$rdPort")
                    put(RIDER_PARENT_PROCESS_ID, ProcessHandle.current().pid().toString())
                    if (aspireHostConfig.resourceServiceEndpointUrl != null)
                        put(RIDER_RESOURCE_SERVICE_ENDPOINT_URL, aspireHostConfig.resourceServiceEndpointUrl)
                    if (aspireHostConfig.resourceServiceApiKey != null)
                        put(RIDER_RESOURCE_SERVICE_API_KEY, aspireHostConfig.resourceServiceApiKey)
                }
            )
        return commandLine
    }
}