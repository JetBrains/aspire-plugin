package com.jetbrains.rider.aspire.services

import com.intellij.execution.process.ProcessHandler
import com.intellij.execution.process.ProcessOutputTypes
import com.intellij.execution.services.ServiceEventListener
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.EDT
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.diagnostic.trace
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.terminal.TerminalExecutionConsole
import com.jetbrains.rd.util.lifetime.Lifetime
import com.jetbrains.rider.aspire.generated.*
import com.jetbrains.rider.aspire.settings.AspireSettings
import com.jetbrains.rider.aspire.util.parseLogEntry
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
    private val aspireHost: AspireHost,
    val lifetime: Lifetime,
    private val project: Project
) : Disposable {
    companion object {
        private val LOG = logger<AspireResource>()
    }

    private var uid: String

    var name: String
        private set
    var type: ResourceType
        private set
    var displayName: String
        private set
    var state: ResourceState?
        private set
    var healthStatus: ResourceHealthStatus? = null
        private set
    var urls: Array<ResourceUrl>
        private set
    var environment: Array<ResourceEnvironmentVariable>
        private set
    var volumes: Array<ResourceVolume>
        private set
    var relationships: Array<ResourceRelationship>
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
    var containerLifetime: String? = null
        private set

    var commands: Array<ResourceCommand>
        private set

    var isUnderDebugger: Boolean? = null
        private set

    private val logProcessHandler = object : ProcessHandler() {
        override fun destroyProcessImpl() {}
        override fun detachProcessImpl() {}
        override fun detachIsDefault() = false
        override fun getProcessInput() = null
    }
    private val logConsole = TerminalExecutionConsole(project, logProcessHandler)
    val logConsoleComponent = logConsole.component

    private val descriptor by lazy { AspireResourceServiceViewDescriptor(this) }

    init {
        val model = modelWrapper.model.valueOrNull

        uid = model?.uid ?: ""
        name = model?.name ?: ""
        type = model?.type ?: ResourceType.Unknown
        displayName = model?.displayName ?: ""
        state = model?.state

        fillHealthStatus(model?.healthReports ?: emptyArray())
        fillDates(model)

        urls = model?.urls ?: emptyArray()
        environment = model?.environment ?: emptyArray()
        volumes = model?.volumes ?: emptyArray()
        relationships = model?.relationships ?: emptyArray()

        fillFromProperties(model?.properties ?: emptyArray())

        commands = model?.commands ?: emptyArray()

        modelWrapper.model.advise(lifetime, ::update)
        modelWrapper.logReceived.advise(lifetime, ::logReceived)

        logProcessHandler.startNotify()

        Disposer.register(this, logConsole)
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

                "executable.path" -> {
                    property.value?.let { executablePath = Path(it) }
                }

                "executable.pid" -> {
                    property.value?.let { pid = it.toInt() }
                }

                "executable.workDir" -> {
                    property.value?.let { executableWorkDir = Path(it) }
                }

                "executable.args" -> {
                    property.value?.let { args = it }
                }

                "container.id" -> {
                    property.value?.let { containerId = it }
                }

                "container.image" -> {
                    property.value?.let { containerImage = it }
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

                "container.lifetime" -> {
                    property.value?.let { containerLifetime = it }
                }
            }
        }
    }

    private fun fillHealthStatus(healthReports: Array<ResourceHealthReport>) {
        if (healthReports.isEmpty()) {
            healthStatus = null
            return
        }

        healthStatus = healthReports.last().status
    }

    fun getViewDescriptor() = descriptor

    private fun update(model: ResourceModel) {
        uid = model.uid
        name = model.name
        val typeJustInitialised = type == ResourceType.Unknown && model.type != ResourceType.Unknown
        type = model.type
        displayName = model.displayName
        state = if (state != ResourceState.Hidden) model.state else ResourceState.Hidden

        fillHealthStatus(model.healthReports)
        fillDates(model)

        urls = model.urls
        environment = model.environment
        volumes = model.volumes
        relationships = model.relationships

        fillFromProperties(model.properties)

        commands = model.commands

        project.messageBus.syncPublisher(ResourceListener.TOPIC).resourceUpdated(this)

        if (typeJustInitialised && state != ResourceState.Hidden) {
            sendServiceChildrenChangedEvent()
        } else if (type != ResourceType.Unknown && state != ResourceState.Hidden) {
            sendServiceChangedEvent()
        }
    }

    fun setProfileData(profileData: AspireProjectResourceProfileData) {
        if (type != ResourceType.Project || isUnderDebugger == profileData.isDebugMode) return

        isUnderDebugger = profileData.isDebugMode

        sendServiceChangedEvent()
    }

    suspend fun executeCommand(commandName: String) = withContext(Dispatchers.EDT) {
        val command = ResourceCommandRequest(
            commandName,
            name,
            type.toString()
        )
        LOG.trace { "Executing command: $command for the resource $uid" }
        val response = modelWrapper.executeCommand.startSuspending(command)
        if (response.kind != ResourceCommandResponseKind.Succeeded) {
            LOG.warn("Unable to execute command: ${response.kind}, ${response.errorMessage}")
        }
    }

    private fun logReceived(log: ResourceLog) {
        val outputType = if (!log.isError) ProcessOutputTypes.STDOUT else ProcessOutputTypes.STDERR
        val (_, logContent) = parseLogEntry(log.text) ?: return
        logProcessHandler.notifyTextAvailable(logContent + "\r\n", outputType)
    }

    private fun sendServiceChangedEvent() {
        val event = ServiceEventListener.ServiceEvent.createEvent(
            ServiceEventListener.EventType.SERVICE_CHANGED,
            this,
            AspireMainServiceViewContributor::class.java
        )
        project.messageBus.syncPublisher(ServiceEventListener.TOPIC).handle(event)
    }

    private fun sendServiceChildrenChangedEvent() {
        val event = ServiceEventListener.ServiceEvent.createEvent(
            ServiceEventListener.EventType.SERVICE_CHILDREN_CHANGED,
            aspireHost,
            AspireMainServiceViewContributor::class.java
        )
        project.messageBus.syncPublisher(ServiceEventListener.TOPIC).handle(event)
    }

    override fun dispose() {
    }
}