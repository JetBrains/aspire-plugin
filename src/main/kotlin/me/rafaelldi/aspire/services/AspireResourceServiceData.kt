package me.rafaelldi.aspire.services

import com.intellij.execution.filters.TextConsoleBuilderFactory
import com.intellij.execution.ui.ConsoleView
import com.intellij.execution.ui.ConsoleViewContentType
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.jetbrains.rd.util.lifetime.LifetimeDefinition
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import me.rafaelldi.aspire.AspireService
import me.rafaelldi.aspire.generated.*
import java.nio.file.Path
import kotlin.io.path.Path
import kotlin.math.roundToInt

class AspireResourceServiceData(
    resourceModel: ResourceModel,
    project: Project,
    private val lifetimeDefinition: LifetimeDefinition
) {
    var name: String
        private set
    var resourceType: ResourceType
        private set
    var displayName: String
        private set
    var state: String?
        private set
    var isRunning: Boolean
        private set
    var endpoints: Array<ResourceEndpoint>
        private set
    var environment: Array<ResourceEnvironmentVariable>
        private set

    var startTime: LocalDateTime? = null
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

    private val consoleView: ConsoleView = TextConsoleBuilderFactory
        .getInstance()
        .createBuilder(project)
        .apply { setViewer(true) }
        .console
    fun getConsole() = consoleView

    init {
        name = resourceModel.name
        resourceType = resourceModel.resourceType
        displayName = resourceModel.displayName
        state = resourceModel.state
        isRunning = resourceModel.state?.equals("running", true) == true
        endpoints = resourceModel.endpoints
        environment = resourceModel.environment

        fillFromProperties(resourceModel.properties)

        resourceModel.logReceived.advise(lifetimeDefinition, ::logReceived)

        Disposer.register(AspireService.getInstance(project), consoleView)
    }

    private fun fillFromProperties(properties: Array<ResourceProperty>) {
        for (property in properties) {
            when (property.name) {
                "resource.createTime" -> {
                    property.value?.let {
                        startTime = Instant.parse(it).toLocalDateTime(TimeZone.currentSystemDefault())
                    }
                }

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

    fun update(resourceModel: ResourceModel) {
        name = resourceModel.name
        resourceType = resourceModel.resourceType
        displayName = resourceModel.displayName
        state = resourceModel.state
        isRunning = resourceModel.state?.equals("running", true) == true
        endpoints = resourceModel.endpoints
        environment = resourceModel.environment

        fillFromProperties(resourceModel.properties)
    }

    fun unsubscribe() {
        lifetimeDefinition.terminate()
    }

    private fun logReceived(log: ResourceLog) {
        consoleView.print(
            log.text + "\n",
            if (!log.isError) ConsoleViewContentType.NORMAL_OUTPUT
            else ConsoleViewContentType.ERROR_OUTPUT
        )
    }
}
