package com.jetbrains.aspire.unit.cli

import com.jetbrains.aspire.cli.AspireCliResource
import com.jetbrains.aspire.cli.AspireCliResourceCommand
import com.jetbrains.aspire.cli.AspireCliResourceHealthReport
import com.jetbrains.aspire.cli.AspireCliResourceUrl
import com.jetbrains.aspire.cli.toAspireResourceUpdate
import com.jetbrains.aspire.resources.AspireResourceChange
import com.jetbrains.aspire.resources.AspireResourceUpdate
import com.jetbrains.aspire.worker.AspireAppHostPath
import com.jetbrains.aspire.worker.AspirePath
import com.jetbrains.aspire.worker.AspireResourceData
import com.jetbrains.aspire.worker.AspireResourceId
import com.jetbrains.aspire.worker.AspireResourceProperty
import com.jetbrains.aspire.worker.ResourceCommandState
import com.jetbrains.aspire.worker.ResourceHealthStatus
import com.jetbrains.aspire.worker.ResourceLaunchConfigurationType
import com.jetbrains.aspire.worker.ResourceState
import com.jetbrains.aspire.worker.ResourceStateStyle
import com.jetbrains.aspire.worker.ResourceType
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.junit.jupiter.params.provider.ValueSource
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

internal class AspireCliResourceConverterTest {
    private val appHostPath = AspireAppHostPath("test/path/AppHost.csproj")

    @Test
    fun `resource becomes an upsert scoped to its AppHost`() {
        val resource = AspireCliResource("api", displayName = "API", resourceType = "Project", state = "Starting", stateStyle = "info")
        val expectedId = AspireResourceId(appHostPath, "api")

        val data = convert(resource)

        assertEquals(expectedId, data.id)
        assertEquals("api", data.uid)
        assertEquals("api", data.name)
        assertEquals("API", data.displayName)
        assertEquals(ResourceType.Project, data.type)
        assertEquals("Project", data.originType)
        assertEquals(ResourceState.Starting, data.state)
        assertEquals(ResourceStateStyle.Info, data.stateStyle)
    }

    @Test
    fun `absent CLI fields use empty or null defaults`() {
        val resource = AspireCliResource("api", resourceType = "Project")

        val data = convert(resource)

        assertEquals("api", data.displayName)
        assertEquals(ResourceLaunchConfigurationType.Unknown, data.launchConfigurationType)
        assertNull(data.state)
        assertNull(data.stateStyle)
        assertNull(data.healthStatus)
        assertFalse(data.isHidden)
        assertTrue(data.urls.isEmpty())
        assertTrue(data.commands.isEmpty())
        assertTrue(data.environment.isEmpty())
        assertTrue(data.volumes.isEmpty())
        assertTrue(data.relationships.isEmpty())
        assertNull(data.parentDisplayName)
        assertNull(data.createdAt)
        assertNull(data.startedAt)
        assertNull(data.stoppedAt)
        assertNull(data.exitCode)
        assertNull(data.pid)
        assertNull(data.projectPath)
    }

    @ParameterizedTest
    @CsvSource("Project, Project", "Container, Container", "Executable, Executable", "Parameter, Parameter",
               "ExternalService, ExternalService", "MongoDBDatabaseResource, MongoDB", "MySqlDatabaseResource, MySql",
               "PostgresDatabaseResource, Postgres", "SqlServerDatabaseResource, SqlServer", "AzureStorageResource, AzureStorageResource",
               "FutureType, Unknown")
    fun `resource types include database aliases and unknown types`(type: String, expected: ResourceType) {
        val resource = AspireCliResource("resource", resourceType = type)

        val data = convert(resource)

        assertEquals(expected, data.type)
        assertEquals(type, data.originType)
    }

    @Test
    fun `unknown states and styles do not interrupt conversion`() {
        val resource = AspireCliResource("api", resourceType = "Project", state = "FutureState", stateStyle = "future-style")

        val data = convert(resource)

        assertEquals(ResourceState.Unknown, data.state)
        assertEquals(ResourceStateStyle.Unknown, data.stateStyle)
    }

    @Test
    fun `hidden state hides the resource`() {
        val resource = AspireCliResource("api", resourceType = "Project", state = "Hidden")

        val data = convert(resource)

        assertTrue(data.isHidden)
    }

    @ParameterizedTest
    @CsvSource("Healthy, Healthy", "Degraded, Degraded", "Unhealthy, Unhealthy")
    fun `running resource uses the CLI health status`(status: String, expected: ResourceHealthStatus) {
        val resource = AspireCliResource("api", resourceType = "Project", state = "Running", healthStatus = status)

        val data = convert(resource)

        assertEquals(expected, data.healthStatus)
    }

    @ParameterizedTest
    @CsvSource("Healthy, Healthy", "Degraded, Degraded", "Unhealthy, Unhealthy", ", Unhealthy")
    fun `health reports are used when summary is absent`(status: String?, expected: ResourceHealthStatus) {
        val reports = mapOf("ready" to AspireCliResourceHealthReport(status))
        val resource = AspireCliResource("api", resourceType = "Project", state = "Running", healthReports = reports)

        val data = convert(resource)

        assertEquals(expected, data.healthStatus)
    }

    @Test
    fun `nonrunning resources have no health status`() {
        val resource = AspireCliResource("api", resourceType = "Project", state = "Exited", healthStatus = "Healthy")

        val data = convert(resource)

        assertNull(data.healthStatus)
    }

    @Test
    fun `URLs preserve endpoint names display names and internal flag`() {
        val urls = listOf(
            AspireCliResourceUrl("http", "Web", "http://localhost:1234", false),
            AspireCliResourceUrl(url = "http://api:8080", isInternal = true),
        )
        val resource = AspireCliResource("api", resourceType = "Project", urls = urls)

        val data = convert(resource)

        assertEquals(listOf("http://localhost:1234", "http://api:8080"), data.urls.map { it.fullUrl })
        assertEquals("http", data.urls[0].endpointName)
        assertEquals("Web", data.urls[0].displayName)
        assertFalse(data.urls[0].isInternal)
        assertTrue(data.urls[1].isInternal)
        assertTrue(data.urls.none { it.isInactive })
    }

    @Test
    fun `commands preserve names descriptions and states`() {
        val commands = mapOf(
            "restart" to AspireCliResourceCommand("Restart", "Restart the resource", "Highlighted", "Enabled"),
            "stop" to AspireCliResourceCommand(state = "Disabled"),
            "hidden" to AspireCliResourceCommand(visibility = "Hidden", state = "Enabled"),
            "future" to AspireCliResourceCommand(state = "FutureState"),
        )
        val resource = AspireCliResource("api", resourceType = "Project", commands = commands)

        val data = convert(resource)

        assertEquals(listOf("restart", "stop", "hidden", "future"), data.commands.map { it.name })
        assertEquals(listOf(ResourceCommandState.Enabled, ResourceCommandState.Disabled, ResourceCommandState.Hidden,
                            ResourceCommandState.Disabled), data.commands.map { it.state })
        assertEquals("Restart", data.commands[0].displayName)
        assertEquals("Restart the resource", data.commands[0].displayDescription)
        assertTrue(data.commands[0].isHighlighted)
        assertEquals("stop", data.commands[1].displayName)
    }

    @Test
    fun `properties populate process paths and container data`() {
        val properties = mapOf(
            "resource.launchConfigurationType" to "project",
            "resource.exitCode" to "7",
            "executable.pid" to "123",
            "project.path" to "/src/api.csproj",
            "executable.path" to "/usr/bin/api",
            "executable.workDir" to "/src",
            "executable.args" to "--port 8080",
            "container.image" to "redis:latest",
            "container.id" to "container-123",
            "container.ports" to "6379",
            "container.command" to "redis-server",
            "container.args" to "--save 60 1",
            "container.lifetime" to "Persistent",
            "resource.connectionString" to "localhost:6379",
            "resource.source" to "redis",
            "Value" to "parameter value",
        ).mapValues { (_, value) -> JsonPrimitive(value) }
        val resource = AspireCliResource("api", resourceType = "Project", properties = properties)

        val data = convert(resource)

        assertEquals(ResourceLaunchConfigurationType.Project, data.launchConfigurationType)
        assertEquals(AspireResourceProperty(7, false), data.exitCode)
        assertEquals(AspireResourceProperty(123, false), data.pid)
        assertEquals(AspireResourceProperty(AspirePath("/src/api.csproj"), false), data.projectPath)
        assertEquals(AspireResourceProperty(AspirePath("/usr/bin/api"), false), data.executablePath)
        assertEquals(AspireResourceProperty(AspirePath("/src"), false), data.executableWorkDir)
        assertEquals("--port 8080", data.args?.value)
        assertEquals("redis:latest", data.containerImage?.value)
        assertEquals("container-123", data.containerId?.value)
        assertEquals("6379", data.containerPorts?.value)
        assertEquals("redis-server", data.containerCommand?.value)
        assertEquals("--save 60 1", data.containerArgs?.value)
        assertEquals("Persistent", data.containerLifetime?.value)
        assertEquals("localhost:6379", data.connectionString?.value)
        assertEquals("redis", data.source?.value)
        assertEquals("parameter value", data.value?.value)
    }

    @Test
    fun `JSON properties support numbers nulls arrays booleans and objects`() {
        val json = """
            {
              "name":"apiservice-zytddxap",
              "resourceType":"Project",
              "properties":{
                "executable.pid":78722,
                "resource.exitCode":0,
                "executable.args":null,
                "executable.path":"dotnet",
                "project.path":"/src/ApiService.csproj",
                "resource.launchConfigurationType":"project",
                "resource.appArgs":[],
                "resource.appArgsSensitivity":[],
                "container.args":["--port",8080],
                "container.ports":[],
                "Value":true,
                "resource.connectionString":{"endpoint":"localhost"}
              }
            }
        """.trimIndent()

        val resource = Json.decodeFromString<AspireCliResource>(json)
        val data = convert(resource)

        assertEquals(78722, data.pid?.value)
        assertEquals(0, data.exitCode?.value)
        assertNull(data.args)
        assertEquals(AspirePath("dotnet"), data.executablePath?.value)
        assertEquals(AspirePath("/src/ApiService.csproj"), data.projectPath?.value)
        assertEquals(ResourceLaunchConfigurationType.Project, data.launchConfigurationType)
        assertEquals("--port 8080", data.containerArgs?.value)
        assertEquals("", data.containerPorts?.value)
        assertEquals("true", data.value?.value)
        assertEquals("""{"endpoint":"localhost"}""", data.connectionString?.value)
    }

    @Test
    fun `explicit exit code and source take precedence over properties`() {
        val properties = mapOf("resource.exitCode" to "1", "resource.source" to "property source")
            .mapValues { (_, value) -> JsonPrimitive(value) }
        val resource = AspireCliResource("api", resourceType = "Project", exitCode = 7, source = "cli source", properties = properties)

        val data = convert(resource)

        assertEquals(7, data.exitCode?.value)
        assertEquals("cli source", data.source?.value)
    }

    @Test
    fun `invalid and null property values are ignored`() {
        val properties = mapOf("resource.exitCode" to "invalid", "executable.pid" to "9999999999999", "project.path" to null)
            .mapValues { (_, value) -> JsonPrimitive(value) }
        val resource = AspireCliResource("api", resourceType = "Project", properties = properties)

        val data = convert(resource)

        assertNull(data.exitCode)
        assertNull(data.pid)
        assertNull(data.projectPath)
    }

    @ParameterizedTest
    @ValueSource(strings = ["true", "[]", "{\"value\":1}", "\"invalid\"", "9999999999999", "null"])
    fun `invalid numeric JSON properties are ignored`(value: String) {
        val json = """
            {"name":"api","resourceType":"Project","properties":{"executable.pid":$value,"resource.exitCode":$value}}
        """.trimIndent()

        val resource = Json.decodeFromString<AspireCliResource>(json)
        val data = convert(resource)

        assertNull(data.pid)
        assertNull(data.exitCode)
    }

    private fun convert(resource: AspireCliResource): AspireResourceData {
        val update = assertIs<AspireResourceUpdate.Changes>(resource.toAspireResourceUpdate(appHostPath))
        return assertIs<AspireResourceChange.Upsert>(update.changes.single()).data
    }
}