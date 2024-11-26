package com.jetbrains.rider.aspire.services

import com.intellij.execution.filters.TextConsoleBuilderFactory
import com.intellij.execution.process.ProcessHandler
import com.intellij.execution.services.ServiceEventListener
import com.intellij.execution.ui.ConsoleView
import com.intellij.execution.ui.ConsoleViewContentType
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.EDT
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.terminal.TerminalExecutionConsole
import com.jetbrains.rd.util.lifetime.Lifetime
import com.jetbrains.rider.aspire.generated.*
import com.jetbrains.rider.aspire.settings.AspireSettings
import com.jetbrains.rider.aspire.util.getServiceInstanceId
import com.jetbrains.rider.debugger.DebuggerWorkerProcessHandler
import com.jetbrains.rider.run.ConsoleKind
import com.jetbrains.rider.run.createConsole
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import java.nio.file.Path
import kotlin.io.path.Path
import kotlin.math.roundToInt

class AspireResource(
    private val modelWrapper: ResourceWrapper,
    val lifetime: Lifetime,
    private val aspireHost: AspireHost,
    private val project: Project
) : Disposable {
    companion object {
        private val LOG = logger<AspireResource>()
    }

    val serviceViewContributor: AspireResourceServiceViewContributor by lazy {
        AspireResourceServiceViewContributor(this)
    }

    var uid: String
        private set
    var name: String
        private set
    var type: ResourceType
        private set
    var displayName: String
        private set
    var state: ResourceState?
        private set
    var healthStatus: ResourceHealthStatus?
        private set
    var urls: Array<ResourceUrl>
        private set
    var environment: Array<ResourceEnvironmentVariable>
        private set
    var volumes: Array<ResourceVolume>
        private set

    var serviceInstanceId: String? = null
        private set

    var createdAt: LocalDateTime? = null
        private set
    var startedAt: LocalDateTime? = null
        private set
    var stoppedAt: LocalDateTime? = null
        private set
    var exitCode: Int? = null
        private set
    var pid: Int? = null
        private set
    var projectPath: Path? = null
        private set
    var executablePath: Path? = null
        private set
    var executableWorkDir: Path? = null
        private set
    var args: String? = null
        private set
    var containerImage: String? = null
        private set
    var containerId: String? = null
        private set
    var containerPorts: String? = null
        private set
    var containerCommand: String? = null
        private set
    var containerArgs: String? = null
        private set

    var commands: Array<ResourceCommand>
        private set

    var isUnderDebugger: Boolean? = null
        private set

    val console = TerminalExecutionConsole(project, null)

    var consoleView: ConsoleView = TextConsoleBuilderFactory
        .getInstance()
        .createBuilder(project)
        .apply { setViewer(true) }
        .console

    init {
        val model = modelWrapper.model.valueOrNull

        uid = model?.uid ?: ""
        name = model?.name ?: ""
        type = model?.type ?: ResourceType.Unknown
        displayName = model?.displayName ?: ""
        state = model?.state
        healthStatus = model?.healthStatus

        fillDates(model)

        urls = model?.urls ?: emptyArray()
        environment = model?.environment ?: emptyArray()
        volumes = model?.volumes ?: emptyArray()

        serviceInstanceId = model?.getServiceInstanceId()

        fillFromProperties(model?.properties ?: emptyArray())

        commands = model?.commands ?: emptyArray()

        modelWrapper.model.advise(lifetime, ::update)
        modelWrapper.logReceived.advise(lifetime, ::logReceived)

        Disposer.register(this, console)
        Disposer.register(this, consoleView)

        project.messageBus.syncPublisher(ResourceListener.TOPIC).resourceCreated(this)

        lifetime.bracketIfAlive({
            sendServiceStructureChangedEvent()
        }, {
            sendServiceStructureChangedEvent()
        })
    }

    private fun fillDates(model: ResourceModel?) {
        val timezone = TimeZone.currentSystemDefault()
        createdAt = model?.createdAt
            ?.time
            ?.let { Instant.fromEpochMilliseconds(it) }
            ?.toLocalDateTime(timezone)
        startedAt = model?.startedAt
            ?.time
            ?.let { Instant.fromEpochMilliseconds(it) }
            ?.toLocalDateTime(timezone)
        stoppedAt = model?.stoppedAt
            ?.time
            ?.let { Instant.fromEpochMilliseconds(it) }
            ?.toLocalDateTime(timezone)
    }

    private fun fillFromProperties(properties: Array<ResourceProperty>) {
        val showSensitiveProperties = AspireSettings.getInstance().showSensitiveProperties

        for (property in properties) {
            if (property.isSensitive == true && !showSensitiveProperties) continue

            when (property.name) {

                "resource.exitCode" -> {
                    property.value?.let { exitCode = it.toDouble().roundToInt() }
                }

                "project.path" -> {
                    property.value?.let { projectPath = Path(it) }
                }

                "executable.pid" -> {
                    property.value?.let { pid = it.toInt() }
                }

                "executable.path" -> {
                    property.value?.let { executablePath = Path(it) }
                }

                "executable.workDir" -> {
                    property.value?.let { executableWorkDir = Path(it) }
                }

                "executable.args" -> {
                    property.value?.let { args = it }
                }

                "container.image" -> {
                    property.value?.let { containerImage = it }
                }

                "container.id" -> {
                    property.value?.let { containerId = it }
                }

                "container.ports" -> {
                    property.value?.let { containerPorts = it }
                }

                "container.command" -> {
                    property.value?.let { containerCommand = it }
                }

                "container.args" -> {
                    property.value?.let { containerArgs = it }
                }
            }
        }
    }

    private fun update(model: ResourceModel) {
        uid = model.uid
        name = model.name
        type = model.type
        displayName = model.displayName
        state = model.state
        healthStatus = model.healthStatus

        fillDates(model)

        urls = model.urls
        environment = model.environment
        volumes = model.volumes

        serviceInstanceId = model.getServiceInstanceId()

        fillFromProperties(model.properties)

        commands = model.commands

        project.messageBus.syncPublisher(ResourceListener.TOPIC).resourceUpdated(this)

        sendServiceChildrenChangedEvent()
    }

    fun setHandler(processHandler: ProcessHandler) {
        if (type != ResourceType.Project) return

        val handler =
            if (processHandler is DebuggerWorkerProcessHandler) {
                isUnderDebugger = true
                processHandler.debuggerWorkerRealHandler
            }
            else {
                isUnderDebugger = false
                processHandler
            }
//        val console = createConsole(
//            ConsoleKind.Normal,
//            handler,
//            project
//        )
//        Disposer.register(this, console)
//        consoleView = console

//        sendServiceChildrenChangedEvent()
    }

    suspend fun executeCommand(commandType: String) = withContext(Dispatchers.EDT) {
        val command = ResourceCommandRequest(
            commandType,
            name,
            type.toString()
        )
        val response = modelWrapper.executeCommand.startSuspending(command)
        if (response.kind != ResourceCommandResponseKind.Succeeded) {
            LOG.warn("Unable to execute command: ${response.kind}, ${response.errorMessage}")
        }
    }

    private fun logReceived(log: ResourceLog) {
//        if (type == ResourceType.Project) return

        console.print(
            log.text, //+ "\n",
            if (!log.isError) ConsoleViewContentType.NORMAL_OUTPUT
            else ConsoleViewContentType.ERROR_OUTPUT
        )
    }

    private fun sendServiceStructureChangedEvent() {
        val serviceEvent = ServiceEventListener.ServiceEvent.createEvent(
            ServiceEventListener.EventType.SERVICE_STRUCTURE_CHANGED,
            aspireHost.serviceViewContributor.asService(),
            AspireMainServiceViewContributor::class.java
        )
        project.messageBus.syncPublisher(ServiceEventListener.TOPIC).handle(serviceEvent)
    }

    private fun sendServiceChildrenChangedEvent() {
        val serviceEvent = ServiceEventListener.ServiceEvent.createEvent(
            ServiceEventListener.EventType.SERVICE_CHILDREN_CHANGED,
            aspireHost.serviceViewContributor.asService(),
            AspireMainServiceViewContributor::class.java
        )
        project.messageBus.syncPublisher(ServiceEventListener.TOPIC).handle(serviceEvent)
    }

    override fun dispose() {
    }
}