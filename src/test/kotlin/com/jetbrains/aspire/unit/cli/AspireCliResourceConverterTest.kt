package com.jetbrains.aspire.unit.cli

import com.jetbrains.aspire.cli.AspireCliResource
import com.jetbrains.aspire.cli.AspireCliResourceCommand
import com.jetbrains.aspire.cli.AspireCliResourceHealthReport
import com.jetbrains.aspire.cli.AspireCliResourceRelationship
import com.jetbrains.aspire.cli.AspireCliResourceUrl
import com.jetbrains.aspire.cli.AspireCliResourceVolume
import com.jetbrains.aspire.cli.toAspireResourceUpdate
import com.jetbrains.aspire.resources.AspireResourceChange
import com.jetbrains.aspire.resources.AspireResourceUpdate
import com.jetbrains.aspire.worker.AspireAppHostId
import com.jetbrains.aspire.worker.AspirePath
import com.jetbrains.aspire.worker.AspireResourceData
import com.jetbrains.aspire.worker.AspireResourceId
import com.jetbrains.aspire.worker.AspireResourceProperty
import com.jetbrains.aspire.worker.ResourceCommandState
import com.jetbrains.aspire.worker.ResourceEnvironmentVariable
import com.jetbrains.aspire.worker.ResourceHealthStatus
import com.jetbrains.aspire.worker.ResourceLaunchConfigurationType
import com.jetbrains.aspire.worker.ResourceRelationship
import com.jetbrains.aspire.worker.ResourceState
import com.jetbrains.aspire.worker.ResourceStateStyle
import com.jetbrains.aspire.worker.ResourceType
import com.jetbrains.aspire.worker.ResourceVolume
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
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
import kotlin.time.Instant

internal class AspireCliResourceConverterTest {
    private val appHostId = AspireAppHostId("AppHost")

    @Test
    fun `resource becomes an upsert scoped to its AppHost`() {
        val resource = AspireCliResource("api", displayName = "API", resourceType = "Project", state = "Starting", stateStyle = "info")
        val expectedId = AspireResourceId(appHostId, "api")

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
    fun `same named resources from different AppHosts have distinct ids`() {
        val resource = AspireCliResource("api", resourceType = "Project")
        val otherAppHostId = AspireAppHostId("OtherHost")

        val first = convert(resource)
        val secondUpdate = assertIs<AspireResourceUpdate.Changes>(resource.toAspireResourceUpdate(otherAppHostId))
        val second = assertIs<AspireResourceChange.Upsert>(secondUpdate.changes.single()).data

        assertEquals(AspireResourceId(appHostId, "api"), first.id)
        assertEquals(AspireResourceId(otherAppHostId, "api"), second.id)
    }

    @Test
    fun `resource UID is preserved separately from its name`() {
        val resource = AspireCliResource("api", uid = "api-123")
        val expectedId = AspireResourceId(appHostId, "api")

        val data = convert(resource)

        assertEquals("api-123", data.uid)
        assertEquals("api", data.name)
        assertEquals(expectedId, data.id)
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
        assertNull(data.parentName)
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
    fun `absent resource type is unknown`() {
        val resource = AspireCliResource("api")

        val data = convert(resource)

        assertEquals(ResourceType.Unknown, data.type)
        assertEquals("", data.originType)
    }

    @ParameterizedTest
    @CsvSource("Building, Building", "Starting, Starting", "Running, Running", "FailedToStart, FailedToStart",
               "RuntimeUnhealthy, RuntimeUnhealthy", "Stopping, Stopping", "Exited, Exited", "Finished, Finished",
               "Waiting, Waiting", "NotStarted, NotStarted", "Hidden, Hidden", "FutureState, Unknown")
    fun `resource states are converted including unknown values`(state: String, expected: ResourceState) {
        val resource = AspireCliResource("api", state = state)

        val data = convert(resource)

        assertEquals(expected, data.state)
    }

    @ParameterizedTest
    @CsvSource("success, Success", "info, Info", "warning, Warning", "error, Error", "future-style, Unknown")
    fun `resource state styles are converted including unknown values`(style: String, expected: ResourceStateStyle) {
        val resource = AspireCliResource("api", stateStyle = style)

        val data = convert(resource)

        assertEquals(expected, data.stateStyle)
    }

    @Test
    fun `unknown states and styles do not interrupt conversion`() {
        val resource = AspireCliResource("api", resourceType = "Project", state = "FutureState", stateStyle = "future-style")

        val data = convert(resource)

        assertEquals(ResourceState.Unknown, data.state)
        assertEquals(ResourceStateStyle.Unknown, data.stateStyle)
    }

    @Test
    fun `hidden state alone does not hide the resource`() {
        val resource = AspireCliResource("api", resourceType = "Project", state = "Hidden")

        val data = convert(resource)

        assertFalse(data.isHidden)
    }

    @ParameterizedTest
    @ValueSource(strings = ["aspire-dashboard-123", "api-rebuilder-123"])
    fun `known internal resource names hide the resource`(name: String) {
        val resource = AspireCliResource(name, state = "Running")

        val data = convert(resource)

        assertTrue(data.isHidden)
    }

    @ParameterizedTest
    @ValueSource(strings = ["api", "aspire-dashboard", "api-aspire-dashboard-123", "api-rebuilder", "rebuilder-123"])
    fun `resource names outside the known hidden patterns remain visible`(name: String) {
        val resource = AspireCliResource(name, state = "Running")

        val data = convert(resource)

        assertFalse(data.isHidden)
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
    fun `endpoints without a URL are ignored`() {
        val endpointWithoutUrl = AspireCliResourceUrl(name = "pending")
        val endpointWithUrl = AspireCliResourceUrl(url = "http://localhost:1234")
        val resource = AspireCliResource("api", urls = listOf(endpointWithoutUrl, endpointWithUrl))

        val data = convert(resource)

        assertEquals(listOf("http://localhost:1234"), data.urls.map { it.fullUrl })
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
    fun `environment preserves variable names and nullable values`() {
        val environment = linkedMapOf("MODE" to "Development", "EMPTY" to "", "UNRESOLVED" to null)
        val resource = AspireCliResource("api", environment = environment)
        val expected = listOf(
            ResourceEnvironmentVariable("MODE", "Development"),
            ResourceEnvironmentVariable("EMPTY", ""),
            ResourceEnvironmentVariable("UNRESOLVED", null),
        )

        val data = convert(resource)

        assertEquals(expected, data.environment)
    }

    @Test
    fun `volumes preserve source target mount type and read only flag`() {
        val bindMount = AspireCliResourceVolume("/data", "/app/data", "bind", true)
        val namedVolume = AspireCliResourceVolume("cache-data", "/cache", "volume")
        val resource = AspireCliResource("api", volumes = listOf(bindMount, namedVolume))
        val expected = listOf(
            ResourceVolume("/data", "/app/data", "bind", true),
            ResourceVolume("cache-data", "/cache", "volume", false),
        )

        val data = convert(resource)

        assertEquals(expected, data.volumes)
    }

    @Test
    fun `omitted volume fields use empty string defaults`() {
        val volume = AspireCliResourceVolume(target = "/data")
        val resource = AspireCliResource("api", volumes = listOf(volume))
        val expected = ResourceVolume("", "/data", "", false)

        val data = convert(resource)

        assertEquals(listOf(expected), data.volumes)
    }

    @ParameterizedTest
    @ValueSource(strings = ["Parent", "parent", "PARENT"])
    fun `relationships preserve targets and types independently of parent name`(parentType: String) {
        val reference = AspireCliResourceRelationship("Reference", "database")
        val parent = AspireCliResourceRelationship(parentType, "group")
        val otherParent = AspireCliResourceRelationship("Parent", "other-group")
        val parentName = JsonPrimitive("parent-resource")
        val properties = mapOf("resource.parentName" to parentName)
        val resource = AspireCliResource("api", relationships = listOf(reference, parent, otherParent), properties = properties)
        val expected = listOf(
            ResourceRelationship("database", "Reference"),
            ResourceRelationship("group", parentType),
            ResourceRelationship("other-group", "Parent"),
        )

        val data = convert(resource)

        assertEquals(expected, data.relationships)
        assertEquals("parent-resource", data.parentName)
    }

    @Test
    fun `parent relationship without parent name property does not set parent name`() {
        val parent = AspireCliResourceRelationship("Parent", "group")
        val resource = AspireCliResource("api", relationships = listOf(parent))
        val expectedRelationship = ResourceRelationship("group", "Parent")

        val data = convert(resource)

        assertEquals(listOf(expectedRelationship), data.relationships)
        assertNull(data.parentName)
    }

    @Test
    fun `relationships without a target are ignored and missing types use an empty string`() {
        val missingTarget = AspireCliResourceRelationship(type = "Parent")
        val missingType = AspireCliResourceRelationship(resourceName = "database")
        val futureType = AspireCliResourceRelationship("FutureType", "cache")
        val resource = AspireCliResource("api", relationships = listOf(missingTarget, missingType, futureType))
        val expected = listOf(ResourceRelationship("database", ""), ResourceRelationship("cache", "FutureType"))

        val data = convert(resource)

        assertEquals(expected, data.relationships)
        assertNull(data.parentName)
    }

    @Test
    fun `timestamps preserve instants and fractional seconds in the system time zone`() {
        val resource = AspireCliResource(
            "api",
            creationTimestamp = "2026-10-22T10:00:00.1234567+02:00",
            startTimestamp = "2026-10-22T04:00:01-04:00",
            stopTimestamp = "2026-10-22T08:01:00Z",
        )
        val timezone = TimeZone.currentSystemDefault()
        val expectedCreatedAt = Instant.parse("2026-10-22T08:00:00.1234567Z").toLocalDateTime(timezone)
        val expectedStartedAt = Instant.parse("2026-10-22T08:00:01Z").toLocalDateTime(timezone)
        val expectedStoppedAt = Instant.parse("2026-10-22T08:01:00Z").toLocalDateTime(timezone)

        val data = convert(resource)

        assertEquals(expectedCreatedAt, data.createdAt)
        assertEquals(expectedStartedAt, data.startedAt)
        assertEquals(expectedStoppedAt, data.stoppedAt)
    }

    @ParameterizedTest
    @ValueSource(strings = ["", "invalid", "2026-10-22T08:00:00", "2026-99-99T08:00:00Z"])
    fun `invalid timestamps do not interrupt conversion of valid fields`(timestamp: String) {
        val resource = AspireCliResource(
            "api",
            creationTimestamp = timestamp,
            startTimestamp = "2026-10-22T08:00:01Z",
            stopTimestamp = timestamp,
        )
        val expectedStartedAt = Instant.parse("2026-10-22T08:00:01Z").toLocalDateTime(TimeZone.currentSystemDefault())

        val data = convert(resource)

        assertNull(data.createdAt)
        assertEquals(expectedStartedAt, data.startedAt)
        assertNull(data.stoppedAt)
    }

    @Test
    fun `properties populate process paths and container data`() {
        val properties = mapOf(
            "resource.launchConfigurationType" to "project",
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
            "Value" to "parameter value",
        ).mapValues { (_, value) -> JsonPrimitive(value) }
        val resource = AspireCliResource("api", resourceType = "Project", exitCode = 7, source = "redis", properties = properties)

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
              "exitCode":0,
              "properties":{
                "executable.pid":78722,
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
    fun `explicit exit code and source are converted`() {
        val resource = AspireCliResource("api", resourceType = "Project", exitCode = 7, source = "cli source")

        val data = convert(resource)

        assertEquals(7, data.exitCode?.value)
        assertEquals("cli source", data.source?.value)
    }

    @Test
    fun `invalid and null property values are ignored`() {
        val properties = mapOf("executable.pid" to "9999999999999", "project.path" to null)
            .mapValues { (_, value) -> JsonPrimitive(value) }
        val resource = AspireCliResource("api", resourceType = "Project", properties = properties)

        val data = convert(resource)

        assertNull(data.pid)
        assertNull(data.projectPath)
    }

    @ParameterizedTest
    @ValueSource(strings = ["true", "[]", "{\"value\":1}", "\"invalid\"", "9999999999999", "null"])
    fun `invalid numeric JSON properties are ignored`(value: String) {
        val json = """
            {"name":"api","resourceType":"Project","properties":{"executable.pid":$value}}
        """.trimIndent()

        val resource = Json.decodeFromString<AspireCliResource>(json)
        val data = convert(resource)

        assertNull(data.pid)
    }

    private fun convert(resource: AspireCliResource): AspireResourceData {
        val update = assertIs<AspireResourceUpdate.Changes>(resource.toAspireResourceUpdate(appHostId))
        return assertIs<AspireResourceChange.Upsert>(update.changes.single()).data
    }
}
