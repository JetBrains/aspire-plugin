package com.jetbrains.rider.aspire.services.a

import com.intellij.execution.ExecutionListener
import com.intellij.execution.ExecutionManager
import com.intellij.execution.process.ProcessHandler
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.execution.services.ServiceEventListener
import com.intellij.execution.services.ServiceViewManager
import com.intellij.execution.ui.ConsoleView
import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.util.application
import com.jetbrains.rider.aspire.run.AspireHostConfiguration
import com.jetbrains.rider.aspire.run.AspireHostConfigurationParameters
import com.jetbrains.rider.debugger.DebuggerWorkerProcessHandler
import com.jetbrains.rider.run.ConsoleKind
import com.jetbrains.rider.run.createConsole
import java.nio.file.Path
import kotlin.io.path.Path
import kotlin.io.path.absolutePathString
import kotlin.io.path.nameWithoutExtension

class AspireHost(
    private val hostProjectPath: Path,
    private val project: Project
) : Disposable {

    private val serviceEventPublisher = project.messageBus.syncPublisher(ServiceEventListener.TOPIC)

    val hostProjectPathString = hostProjectPath.absolutePathString()

    val serviceViewContributor: AspireHostServiceViewContributor by lazy {
        AspireHostServiceViewContributor(this)
    }

    var displayName: String = hostProjectPath.nameWithoutExtension
        private set
    var isActive: Boolean = false
        private set
    var dashboardUrl: String? = null
        private set
    var consoleView: ConsoleView? = null
        private set

    init {
        project.messageBus.connect(this).subscribe(
            ExecutionManager.EXECUTION_TOPIC,
            object : ExecutionListener {
                override fun processStarted(
                    executorId: String,
                    env: ExecutionEnvironment,
                    handler: ProcessHandler
                ) {
                    val profile = env.runProfile
                    if (profile !is AspireHostConfiguration) return

                    val projectFilePath = Path(profile.parameters.projectFilePath)
                    if (hostProjectPath != projectFilePath) return

                    start(handler, profile.parameters)
                }

                override fun processTerminated(
                    executorId: String,
                    env: ExecutionEnvironment,
                    handler: ProcessHandler,
                    exitCode: Int
                ) {
                    val profile = env.runProfile
                    if (profile !is AspireHostConfiguration) return

                    val projectFilePath = Path(profile.parameters.projectFilePath)
                    if (hostProjectPath != projectFilePath) return

                    stop()
                }
            })
    }

    fun getResources(): List<AspireResource> {
        return emptyList()
    }

    private fun start(handler: ProcessHandler, parameters: AspireHostConfigurationParameters) {
        isActive = true
        dashboardUrl = parameters.startBrowserParameters.url

        val handler =
            if (handler is DebuggerWorkerProcessHandler) handler.debuggerWorkerRealHandler
            else handler
        val console = createConsole(
            ConsoleKind.Normal,
            handler,
            project
        )
        Disposer.register(this, console)
        consoleView = console

        selectHost()

        sendServiceChangedEvent()
    }

    private fun stop() {
        isActive = false
        dashboardUrl = null

        sendServiceChangedEvent()
    }

    private fun selectHost() {
        application.invokeLater {
            ServiceViewManager
                .getInstance(project)
                .select(serviceViewContributor.asService(), AspireMainServiceViewContributor::class.java, true, true)
        }
    }

    private fun expand() {
        application.invokeLater {
            ServiceViewManager
                .getInstance(project)
                .expand(serviceViewContributor.asService(), AspireMainServiceViewContributor::class.java)
        }
    }

    private fun sendServiceChangedEvent() {
        val event = ServiceEventListener.ServiceEvent.createEvent(
            ServiceEventListener.EventType.SERVICE_CHANGED,
            serviceViewContributor.asService(),
            AspireMainServiceViewContributor::class.java
        )
        serviceEventPublisher.handle(event)
    }

    override fun dispose() {
    }
}