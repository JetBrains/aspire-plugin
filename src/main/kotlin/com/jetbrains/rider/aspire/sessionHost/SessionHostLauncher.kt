package com.jetbrains.rider.aspire.sessionHost

import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.process.KillableColoredProcessHandler
import com.intellij.execution.process.ProcessEvent
import com.intellij.execution.process.ProcessListener
import com.intellij.execution.process.ProcessOutputType
import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.diagnostic.trace
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.project.Project
import com.intellij.openapi.rd.createNestedDisposable
import com.intellij.openapi.util.Key
import com.jetbrains.rd.util.lifetime.LifetimeDefinition
import com.jetbrains.rider.NetCoreRuntime
import com.jetbrains.rider.aspire.util.decodeAnsiCommandsToString
import java.nio.charset.StandardCharsets
import java.nio.file.Path
import kotlin.io.path.div

@Service(Service.Level.PROJECT)
class SessionHostLauncher {
    companion object {
        fun getInstance(project: Project) = project.service<SessionHostLauncher>()

        private val LOG = logger<SessionHostLauncher>()

        private const val RIDER_PARENT_PROCESS_ID = "RIDER_PARENT_PROCESS_ID"
        private const val RIDER_DCP_SESSION_TOKEN = "RIDER_DCP_SESSION__Token"
        private const val RIDER_RD_PORT = "RIDER_CONNECTION__RdPort"
    }

    private val pluginId = PluginId.getId("me.rafaelldi.aspire")

    private val hostAssemblyPath: Path = run {
        val plugin = PluginManagerCore.getPlugin(pluginId) ?: error("Plugin $pluginId could not be found.")
        val basePath = plugin.pluginPath ?: error("Could not detect path of plugin $plugin on disk.")
        basePath / "aspire-session-host" / "aspire-session-host.dll"
    }

    fun launchSessionHost(config: SessionHostConfig, lifetime: LifetimeDefinition) {
        LOG.info("Starting Aspire session host")

        val dotnetCliPath = NetCoreRuntime.cliPath.value

        val commandLine = getCommandLine(dotnetCliPath, config)
        LOG.trace { "Host command line: ${commandLine.commandLineString}" }

        val processHandler = KillableColoredProcessHandler.Silent(commandLine)
        lifetime.onTermination {
            if (!processHandler.isProcessTerminating && !processHandler.isProcessTerminated) {
                LOG.info("Aspire session host lifetime was terminated; killing the process")
                processHandler.killProcess()
            }
        }
        processHandler.addProcessListener(object : ProcessListener {
            override fun onTextAvailable(event: ProcessEvent, outputType: Key<*>) {
                val text = decodeAnsiCommandsToString(event.text, outputType).trim()
                if (outputType == ProcessOutputType.STDERR) {
                    LOG.warn(text)
                } else {
                    LOG.debug(text)
                }
            }

            override fun processTerminated(event: ProcessEvent) {
                lifetime.executeIfAlive {
                    LOG.info("Aspire session host process was terminated; terminating the lifetime")
                    lifetime.terminate(true)
                }
            }
        }, lifetime.createNestedDisposable())

        processHandler.startNotify()
        LOG.trace("Aspire session host started")
    }

    private fun getCommandLine(dotnetCliPath: String, config: SessionHostConfig): GeneralCommandLine {
        val commandLine = GeneralCommandLine()
            .withExePath(dotnetCliPath)
            .withCharset(StandardCharsets.UTF_8)
            .withParameters(hostAssemblyPath.toString())
            .withWorkDirectory(hostAssemblyPath.parent.toFile())
            .withEnvironment(
                buildMap {
                    put("Kestrel__Endpoints__Http__Url", "http://localhost:${config.debugSessionPort}/")
                    put(RIDER_RD_PORT, "${config.rdPort}")
                    put(RIDER_PARENT_PROCESS_ID, ProcessHandle.current().pid().toString())
                    put(RIDER_DCP_SESSION_TOKEN, config.debugSessionToken)
                }
            )
        return commandLine
    }
}