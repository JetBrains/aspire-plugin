package com.jetbrains.rider.aspire.worker

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
import com.jetbrains.rider.RiderEnvironment
import com.jetbrains.rider.aspire.util.decodeAnsiCommandsToString
import java.nio.charset.StandardCharsets
import java.nio.file.Path
import java.text.SimpleDateFormat
import java.util.*
import kotlin.io.path.absolutePathString
import kotlin.io.path.div

/**
 * Service responsible for launching an Aspire worker.
 *
 * The worker is launched as an external .NET process.
 * It acts like a proxy between the plugin and aspire services.
 * It receives commands from DCP to run/debug projects, sends project logs back,
 * connects to the resource-endpoint, and converts everything to the RD model.
 *
 * @see <a href="https://github.com/dotnet/aspire/blob/main/docs/specs/IDE-execution.md">.NET Aspire IDE execution</a>
 */
@Service(Service.Level.PROJECT)
class AspireWorkerLauncher {
    companion object {
        fun getInstance(project: Project) = project.service<AspireWorkerLauncher>()

        private val LOG = logger<AspireWorkerLauncher>()

        private const val RIDER_PARENT_PROCESS_ID = "RIDER_PARENT_PROCESS_ID"
        private const val RIDER_DCP_SESSION_TOKEN = "RIDER_DCP_SESSION__Token"
        private const val RIDER_RD_PORT = "RIDER_CONNECTION__RdPort"
        private const val SERILOG_FILE_PATH = "Serilog__WriteTo__0__Args__configure__0__Args__path"
    }

    private val pluginId = PluginId.getId("me.rafaelldi.aspire")

    private val hostAssemblyPath: Path = run {
        val plugin = PluginManagerCore.getPlugin(pluginId) ?: error("Plugin $pluginId could not be found.")
        val basePath = plugin.pluginPath ?: error("Could not detect path of plugin $plugin on disk.")
        basePath / "AspireWorker" / "AspireWorker.dll"
    }

    /**
     * Launches the Aspire worker process with the specified configuration and lifetime management.
     *
     * @param config The configuration parameters for the Aspire worker, such as port details, session details, and HTTPS usage.
     * @param lifetime The lifetime definition to control the lifecycle of the Aspire worker process, ensuring proper termination and cleanup.
     */
    fun launchWorker(config: AspireWorkerConfig, lifetime: LifetimeDefinition) {
        LOG.info("Starting Aspire worker")

        val dotnetCliPath = NetCoreRuntime.cliPath.value

        val commandLine = getCommandLine(dotnetCliPath, config)
        LOG.trace { "Host command line: ${commandLine.commandLineString}" }

        val processHandler = KillableColoredProcessHandler.Silent(commandLine)
        lifetime.onTermination {
            if (!processHandler.isProcessTerminating && !processHandler.isProcessTerminated) {
                LOG.info("Aspire worker lifetime was terminated; killing the process")
                processHandler.destroyProcess()
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
                    LOG.info("Aspire worker process was terminated; terminating the lifetime")
                    lifetime.terminate(true)
                }
            }
        }, lifetime.createNestedDisposable())

        processHandler.startNotify()
        LOG.trace("Aspire worker started")
    }

    private fun getCommandLine(dotnetCliPath: String, config: AspireWorkerConfig): GeneralCommandLine {
        val logFile = getLogFile()
        val commandLine = GeneralCommandLine()
            .withExePath(dotnetCliPath)
            .withCharset(StandardCharsets.UTF_8)
            .withParameters(hostAssemblyPath.toString())
            .withWorkDirectory(hostAssemblyPath.parent.toFile())
            .withEnvironment(
                buildMap {
                    if (config.useHttps) {
                        put("Kestrel__Endpoints__Https__Url", "https://localhost:${config.debugSessionPort}/")
                    } else {
                        put("Kestrel__Endpoints__Http__Url", "http://localhost:${config.debugSessionPort}/")
                    }
                    put(RIDER_RD_PORT, "${config.rdPort}")
                    put(RIDER_PARENT_PROCESS_ID, ProcessHandle.current().pid().toString())
                    put(RIDER_DCP_SESSION_TOKEN, config.debugSessionToken)
                    put(SERILOG_FILE_PATH, logFile.absolutePathString())
                }
            )
        return commandLine
    }

    private fun getLogFile(): Path {
        val aspireWorkerLogFolder = RiderEnvironment.logDirectory.toPath().resolve("AspireWorker")
        val format = SimpleDateFormat("yyyy_M_dd_HH_mm_ss")
        val currentTimeString = format.format(Date())
        val logFileName = "$currentTimeString.log"
        return aspireWorkerLogFolder.resolve(logFileName)
    }
}