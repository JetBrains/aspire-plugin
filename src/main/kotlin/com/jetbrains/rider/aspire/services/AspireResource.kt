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
    var isHidden: Boolean
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
    var exitCode: AspireResourceProperty<Int>? = null
        private set
    var pid: AspireResourceProperty<Int>? = null
        private set
    var projectPath: AspireResourceProperty<Path>? = null
        private set
    var executablePath: AspireResourceProperty<Path>? = null
        private set
    var executableWorkDir: AspireResourceProperty<Path>? = null
        private set
    var args: AspireResourceProperty<String>? = null
        private set
    var containerImage: AspireResourceProperty<String>? = null
        private set
    var containerId: AspireResourceProperty<String>? = null
        private set
    var containerPorts: AspireResourceProperty<String>? = null
        private set
    var containerCommand: AspireResourceProperty<String>? = null
        private set
    var containerArgs: AspireResourceProperty<String>? = null
        private set
    var containerLifetime: AspireResourceProperty<String>? = null
        private set
    var connectionString: AspireResourceProperty<String>? = null
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
        isHidden = model?.isHidden ?: false

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
        for (property in properties) {
            when (property.name) {

                "resource.exitCode" -> {
                    property.value?.let {
                        exitCode = AspireResourceProperty(it.toDouble().roundToInt(), property.isSensitive == true)
                    }
                }

                "project.path" -> {
                    property.value?.let {
                        projectPath = AspireResourceProperty(Path(it), property.isSensitive == true)
                    }
                }

                "executable.path" -> {
                    property.value?.let {
                        executablePath = AspireResourceProperty(Path(it), property.isSensitive == true)
                    }
                }

                "executable.pid" -> {
                    property.value?.let {
                        pid = AspireResourceProperty(it.toInt(), property.isSensitive == true)
                    }
                }

                "executable.workDir" -> {
                    property.value?.let {
                        executableWorkDir = AspireResourceProperty(Path(it), property.isSensitive == true)
                    }
                }

                "executable.args" -> {
                    property.value?.let {
                        args = AspireResourceProperty(it, property.isSensitive == true)
                    }
                }

                "container.id" -> {
                    property.value?.let {
                        containerId = AspireResourceProperty(it, property.isSensitive == true)
                    }
                }

                "container.image" -> {
                    property.value?.let {
                        containerImage = AspireResourceProperty(it, property.isSensitive == true)
                    }
                }

                "container.ports" -> {
                    property.value?.let {
                        containerPorts = AspireResourceProperty(it, property.isSensitive == true)
                    }
                }

                "container.command" -> {
                    property.value?.let {
                        containerCommand = AspireResourceProperty(it, property.isSensitive == true)
                    }
                }

                "container.args" -> {
                    property.value?.let {
                        containerArgs = AspireResourceProperty(it, property.isSensitive == true)
                    }
                }

                "container.lifetime" -> {
                    property.value?.let {
                        containerLifetime = AspireResourceProperty(it, property.isSensitive == true)
                    }
                }

                "resource.connectionString" -> {
                    property.value?.let {
                        connectionString = AspireResourceProperty(it, property.isSensitive == true)
                    }
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
        isHidden = model.isHidden

        fillHealthStatus(model.healthReports)
        fillDates(model)

        urls = model.urls
        environment = model.environment
        volumes = model.volumes
        relationships = model.relationships

        fillFromProperties(model.properties)

        commands = model.commands

        project.messageBus.syncPublisher(ResourceListener.TOPIC).resourceUpdated(this)

        if (typeJustInitialised && !isHidden && state != ResourceState.Hidden) {
            sendServiceChildrenChangedEvent()
        } else if (type != ResourceType.Unknown && !isHidden && state != ResourceState.Hidden) {
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
        LOG.trace { "Received log: $log for the resource $uid" }
        val outputType = if (!log.isError) ProcessOutputTypes.STDOUT else ProcessOutputTypes.STDERR

        val (_, logContent) = parseLogEntry(log.text) ?: run {
            // In some situations (when receiving a huge multiline string in one go),
            // Aspire will send us all strings NOT prefixed by timestamp, but with line endings preserved.
            // Let's just trim those.
            null to log.text
        }

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

    data class AspireResourceProperty<T>(val value: T, val isSensitive: Boolean)
}