package com.jetbrains.aspire.unit.worker

import com.intellij.testFramework.TestApplicationManager
import com.jetbrains.aspire.generated.dashboard.Resource
import com.jetbrains.aspire.generated.dashboard.ResourceCommandRequest
import com.jetbrains.aspire.generated.dashboard.ResourceCommandResponse
import com.jetbrains.aspire.resources.AspireResourceCommandExecutor
import com.jetbrains.aspire.resources.AspireResourceLogWatcher
import com.jetbrains.aspire.resources.grpc.toAspireResourceData
import com.jetbrains.aspire.worker.AspireAppHostPath
import com.jetbrains.aspire.worker.AspireResource
import com.jetbrains.aspire.worker.AspireResourceData
import com.jetbrains.aspire.worker.AspireResourceLogEntry
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertSame

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
internal class AspireResourceTest {
    @BeforeAll
    fun setUpApplication() {
        TestApplicationManager.getInstance()
    }

    @Test
    fun `console logs are collected through the resource watcher`() = runTest {
        val data = createResourceData()
        val watcher = TestResourceLogWatcher()
        val commandExecutor = TestResourceCommandExecutor()
        val resource = AspireResource(data.name, data, this, watcher, commandExecutor)
        val stdoutEntry = AspireResourceLogEntry("Resource output", false)
        val stderrEntry = AspireResourceLogEntry("Resource error", true)
        val logEntries = listOf(stdoutEntry, stderrEntry)
        testScheduler.runCurrent()

        watcher.consoleLogUpdates.emit(logEntries)
        testScheduler.runCurrent()

        assertEquals(listOf(data.name), watcher.watchedResourceNames)
        assertEquals(logEntries, resource.logFlow.replayCache)

        resource.dispose()
    }

    @Test
    fun `commands are delegated to the separate resource command executor`() = runTest {
        val data = createResourceData()
        val watcher = TestResourceLogWatcher()
        val expectedResponse = ResourceCommandResponse.newBuilder()
            .setMessage("Command completed")
            .build()
        val commandExecutor = TestResourceCommandExecutor(expectedResponse)
        val resource = AspireResource(data.name, data, this, watcher, commandExecutor)
        val expectedRequest = ResourceCommandRequest.newBuilder()
            .setCommandName("restart")
            .setResourceName(data.name)
            .setResourceType(data.originType)
            .build()

        val response = resource.executeCommand("restart")

        assertEquals(listOf(expectedRequest), commandExecutor.requests)
        assertSame(expectedResponse, response)

        resource.dispose()
    }

    @Test
    fun `disposing a resource does not shut down the shared client`() = runTest {
        val data = createResourceData()
        val client = MockAspireResourceClient()
        val resource = AspireResource(data.name, data, this, client, client)
        testScheduler.runCurrent()

        resource.dispose()
        testScheduler.runCurrent()

        assertFalse(client.isShutdown.value)
    }

    private fun createResourceData(): AspireResourceData {
        val resource = Resource.newBuilder()
            .setName("api")
            .setDisplayName("API")
            .setResourceType("Project")
            .build()
        val appHostPath = AspireAppHostPath("test/path/AppHost.csproj")
        return resource.toAspireResourceData(appHostPath)
    }

    private class TestResourceLogWatcher : AspireResourceLogWatcher {
        val consoleLogUpdates = MutableSharedFlow<List<AspireResourceLogEntry>>(extraBufferCapacity = 64)
        val watchedResourceNames = mutableListOf<String>()

        override fun watchResourceConsoleLogs(resourceName: String): Flow<List<AspireResourceLogEntry>> {
            watchedResourceNames.add(resourceName)
            return consoleLogUpdates
        }
    }

    private class TestResourceCommandExecutor(
        private val response: ResourceCommandResponse = ResourceCommandResponse.getDefaultInstance(),
    ) : AspireResourceCommandExecutor {
        val requests = mutableListOf<ResourceCommandRequest>()

        override suspend fun executeResourceCommand(request: ResourceCommandRequest): ResourceCommandResponse {
            requests.add(request)
            return response
        }
    }
}
