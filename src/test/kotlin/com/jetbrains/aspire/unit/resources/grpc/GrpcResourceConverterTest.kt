package com.jetbrains.aspire.unit.resources.grpc

import com.jetbrains.aspire.generated.dashboard.ConsoleLogLine
import com.jetbrains.aspire.generated.dashboard.InitialResourceData
import com.jetbrains.aspire.generated.dashboard.Resource
import com.jetbrains.aspire.generated.dashboard.ResourceDeletion
import com.jetbrains.aspire.generated.dashboard.WatchResourceConsoleLogsUpdate
import com.jetbrains.aspire.generated.dashboard.WatchResourcesChange
import com.jetbrains.aspire.generated.dashboard.WatchResourcesChanges
import com.jetbrains.aspire.generated.dashboard.WatchResourcesUpdate
import com.jetbrains.aspire.resources.AspireResourceChange
import com.jetbrains.aspire.resources.AspireResourceUpdate
import com.jetbrains.aspire.resources.grpc.toAspireResourceLogEntries
import com.jetbrains.aspire.resources.grpc.toAspireResourceUpdate
import com.jetbrains.aspire.worker.AspireAppHostPath
import com.jetbrains.aspire.worker.AspireResourceId
import com.jetbrains.aspire.worker.AspireResourceLogEntry
import com.jetbrains.aspire.worker.ResourceState
import com.jetbrains.aspire.worker.ResourceType
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

internal class GrpcResourceConverterTest {
    @Test
    fun `initial data contains converted resources with AppHost scoped ids`() {
        val appHostPath = AspireAppHostPath("test/path/AppHost.csproj")
        val api = resource("api", "Starting")
        val worker = resource("worker", "Running")
        val initialData = InitialResourceData.newBuilder()
            .addResources(api)
            .addResources(worker)
            .build()
        val update = WatchResourcesUpdate.newBuilder().setInitialData(initialData).build()
        val apiId = AspireResourceId(appHostPath, "api")
        val workerId = AspireResourceId(appHostPath, "worker")

        val converted = update.toAspireResourceUpdate(appHostPath)

        val snapshot = assertIs<AspireResourceUpdate.InitialData>(converted)
        assertEquals(listOf(apiId, workerId), snapshot.resources.map { it.id })
        assertEquals(listOf(ResourceState.Starting, ResourceState.Running), snapshot.resources.map { it.state })
        assertTrue(snapshot.resources.all { it.type == ResourceType.Project })
    }

    @Test
    fun `empty initial data remains a snapshot`() {
        val appHostPath = AspireAppHostPath("test/path/AppHost.csproj")
        val initialData = InitialResourceData.getDefaultInstance()
        val update = WatchResourcesUpdate.newBuilder().setInitialData(initialData).build()

        val converted = update.toAspireResourceUpdate(appHostPath)

        val snapshot = assertIs<AspireResourceUpdate.InitialData>(converted)
        assertTrue(snapshot.resources.isEmpty())
    }

    @Test
    fun `changes preserve upsert and delete order while ignoring unset changes`() {
        val appHostPath = AspireAppHostPath("test/path/AppHost.csproj")
        val startingResource = resource("api", "Starting")
        val startingChange = WatchResourcesChange.newBuilder().setUpsert(startingResource).build()
        val deletion = ResourceDeletion.newBuilder().setResourceName("api").build()
        val deleteChange = WatchResourcesChange.newBuilder().setDelete(deletion).build()
        val unsetChange = WatchResourcesChange.getDefaultInstance()
        val runningResource = resource("api", "Running")
        val runningChange = WatchResourcesChange.newBuilder().setUpsert(runningResource).build()
        val changes = WatchResourcesChanges.newBuilder()
            .addValue(startingChange)
            .addValue(deleteChange)
            .addValue(unsetChange)
            .addValue(runningChange)
            .build()
        val update = WatchResourcesUpdate.newBuilder().setChanges(changes).build()
        val expectedId = AspireResourceId(appHostPath, "api")

        val converted = update.toAspireResourceUpdate(appHostPath)

        val batch = assertIs<AspireResourceUpdate.Changes>(converted)
        assertEquals(3, batch.changes.size)
        val starting = assertIs<AspireResourceChange.Upsert>(batch.changes[0])
        val deleted = assertIs<AspireResourceChange.Delete>(batch.changes[1])
        val running = assertIs<AspireResourceChange.Upsert>(batch.changes[2])
        assertEquals(expectedId, starting.data.id)
        assertEquals(ResourceState.Starting, starting.data.state)
        assertEquals("api", deleted.resourceName)
        assertEquals(expectedId, running.data.id)
        assertEquals(ResourceState.Running, running.data.state)
    }

    @Test
    fun `update without a payload is ignored`() {
        val appHostPath = AspireAppHostPath("test/path/AppHost.csproj")
        val update = WatchResourcesUpdate.getDefaultInstance()

        val converted = update.toAspireResourceUpdate(appHostPath)

        assertNull(converted)
    }

    @Test
    fun `console logs are converted in order without timestamps and with stderr flags`() {
        val stdoutLine = ConsoleLogLine.newBuilder()
            .setText("2026-10-07T12:34:56.123Z Resource output")
            .build()
        val stderrLine = ConsoleLogLine.newBuilder()
            .setText("2026-10-07T12:34:57+02:00 Resource error")
            .setIsStdErr(true)
            .build()
        val update = WatchResourceConsoleLogsUpdate.newBuilder()
            .addLogLines(stdoutLine)
            .addLogLines(stderrLine)
            .build()
        val expectedStdout = AspireResourceLogEntry("Resource output", false)
        val expectedStderr = AspireResourceLogEntry("Resource error", true)

        val converted = update.toAspireResourceLogEntries()

        assertEquals(listOf(expectedStdout, expectedStderr), converted)
    }

    @Test
    fun `empty console log lines are ignored while whitespace is preserved`() {
        val emptyLine = ConsoleLogLine.getDefaultInstance()
        val whitespaceLine = ConsoleLogLine.newBuilder()
            .setText(" \r\n")
            .build()
        val update = WatchResourceConsoleLogsUpdate.newBuilder()
            .addLogLines(emptyLine)
            .addLogLines(whitespaceLine)
            .build()
        val expectedEntry = AspireResourceLogEntry(" \r\n", false)

        val converted = update.toAspireResourceLogEntries()

        assertEquals(listOf(expectedEntry), converted)
    }

    @Test
    fun `console logs without timestamps preserve multiline text`() {
        val text = "First line\r\nSecond line\n"
        val logLine = ConsoleLogLine.newBuilder()
            .setText(text)
            .setIsStdErr(true)
            .build()
        val update = WatchResourceConsoleLogsUpdate.newBuilder()
            .addLogLines(logLine)
            .build()
        val expectedEntry = AspireResourceLogEntry(text, true)

        val converted = update.toAspireResourceLogEntries()

        assertEquals(listOf(expectedEntry), converted)
    }

    private fun resource(name: String, state: String): Resource = Resource.newBuilder()
        .setName(name)
        .setResourceType("Project")
        .setState(state)
        .build()
}
