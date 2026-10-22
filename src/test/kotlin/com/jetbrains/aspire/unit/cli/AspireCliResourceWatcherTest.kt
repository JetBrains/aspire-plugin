package com.jetbrains.aspire.unit.cli

import com.intellij.execution.ExecutionException
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.util.ExceptionUtil
import com.jetbrains.aspire.cli.AspireCliResourceWatcher
import com.jetbrains.aspire.worker.AspireResourceLogEntry
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import org.junit.jupiter.api.Test
import java.io.IOException
import kotlin.test.*
import kotlin.time.Duration.Companion.seconds

@OptIn(ExperimentalCoroutinesApi::class)
internal class AspireCliResourceWatcherTest {
    @Test
    fun `process starts only on collection and each collector gets a new process`() = runTest(timeout = 10.seconds) {
        var starts = 0
        val watcher = AspireCliResourceWatcher {
            starts++
            val stdout = TestReceiveChannel("""{"name":"api","resourceType":"Project"}""")
            TestProcess(stdout = stdout)
        }
        val updates = watcher.watchResources()

        assertEquals(0, starts)
        assertEquals(listOf("api"), updates.toList().map { it.name })
        assertEquals(listOf("api"), updates.toList().map { it.name })
        assertEquals(2, starts)
        assertEquals(0L, testScheduler.currentTime)
    }

    @Test
    fun `individual resource updates handle split UTF-8 CRLF and final unterminated line`() = timeoutRunBlocking {
        val output = """
            {"name":"api","resourceType":"Project","displayName":"Application 🚀","state":"Starting"}
            {"name":"api","resourceType":"Project","displayName":"Application 🚀","state":"Running"}
        """.trimIndent().replace("\n", "\r\n")
        val stdout = TestReceiveChannel(output, chunkSize = 1)
        val process = TestProcess(stdout = stdout)
        val watcher = AspireCliResourceWatcher { process }

        val updates = watcher.watchResources().toList()

        assertEquals(listOf("Starting", "Running"), updates.map { it.state })
        assertTrue(updates.all { it.name == "api" && it.displayName == "Application 🚀" })
        assertFalse(process.killed)
    }

    @Test
    fun `blank malformed and non-resource lines do not interrupt updates`() = timeoutRunBlocking {
        val output = """

            Aspire CLI
            {invalid json}
            {}
            {"name":"","resourceType":"Project"}
            {"name":null,"resourceType":"Project"}
            {"name":"   ","resourceType":"Project"}
            {"name":"api","resourceType":"Project","futureField":true}
        """.trimIndent()
        val stdout = TestReceiveChannel(output)
        val process = TestProcess(stdout = stdout)
        val watcher = AspireCliResourceWatcher { process }

        val updates = watcher.watchResources().toList()

        assertEquals(listOf("api"), updates.map { it.name })
    }

    @Test
    fun `resources without a type still produce updates`() = runTest(timeout = 10.seconds) {
        val output = """
            {"name":"missing-type"}
            {"name":"null-type","resourceType":null}
        """.trimIndent()
        val stdout = TestReceiveChannel(output)
        val process = TestProcess(stdout = stdout)
        val watcher = AspireCliResourceWatcher { process }

        val updates = watcher.watchResources().toList()

        assertEquals(listOf("missing-type", "null-type"), updates.map { it.name })
        assertTrue(updates.all { it.resourceType == null })
    }

    @Test
    fun `nested resource models are deserialized`() = runTest(timeout = 10.seconds) {
        val output = """
            {"name":"api","resourceType":"Project","healthReports":{"ready":{"status":"Healthy"}},"urls":[{"url":"https://localhost:1234","isInternal":false}],"properties":{"pid":"123","optional":null,"executable.pid":78722,"resource.appArgs":[]},"commands":{"restart":{"argumentInputs":[{"name":"force","inputType":"Boolean","required":true}]}}}
        """.trimIndent()
        val expectedProperties = mapOf(
            "pid" to JsonPrimitive("123"),
            "optional" to null,
            "executable.pid" to JsonPrimitive(78722),
            "resource.appArgs" to JsonArray(emptyList()),
        )
        val stdout = TestReceiveChannel(output)
        val process = TestProcess(stdout = stdout)
        val watcher = AspireCliResourceWatcher { process }

        val resource = watcher.watchResources().toList().single()

        assertEquals("Healthy", resource.healthReports?.get("ready")?.status)
        assertEquals("https://localhost:1234", resource.urls?.single()?.url)
        assertEquals(expectedProperties, resource.properties)
        val argument = resource.commands?.get("restart")?.argumentInputs?.single()
        assertEquals("Boolean", argument?.inputType)
        assertEquals(true, argument?.required)
    }

    @Test
    fun `logs preserve content and error flags across split UTF-8 CRLF and final unterminated line`() = timeoutRunBlocking {
        val output = """
            {"resourceName":"API","timestamp":"2026-10-16T12:00:00Z","content":"  Application started 🚀","isError":false}
            {"resourceName":"api","content":"Error\n  at Program.Main()","isError":true}
        """.trimIndent().replace("\n", "\r\n")
        val stdout = TestReceiveChannel(output, chunkSize = 1)
        val process = TestProcess(stdout = stdout)
        val watcher = AspireCliResourceWatcher { process }
        val outputEntry = AspireResourceLogEntry("  Application started 🚀", false)
        val errorEntry = AspireResourceLogEntry("Error\n  at Program.Main()", true)
        val expected = listOf(listOf(outputEntry), listOf(errorEntry))

        val logs = watcher.watchResourceConsoleLogs().toList()

        assertEquals(expected, logs)
        assertFalse(process.killed)
    }

    @Test
    fun `blank malformed unrelated and empty log records do not interrupt logs`() = timeoutRunBlocking {
        val output = """

            Aspire CLI
            {invalid json}
            {}
            {"resourceName":"api","resourceType":"Project"}
            {"resourceName":"api","content":"missing error flag"}
            {"name":"other","content":"unrelated","isError":false}
            {"resourceName":"api","content":"","isError":false}
            {"resourceName":"api","content":"output","isError":false,"futureField":true}
        """.trimIndent()
        val stdout = TestReceiveChannel(output)
        val process = TestProcess(stdout = stdout)
        val watcher = AspireCliResourceWatcher { process }
        val expectedEntry = AspireResourceLogEntry("output", false)
        val expected = listOf(listOf(expectedEntry))

        val logs = watcher.watchResourceConsoleLogs().toList()

        assertEquals(expected, logs)
    }

    @Test
    fun `spawn failure reaches the collector`() = timeoutRunBlocking {
        val failure = IOException("CLI unavailable")
        val watcher = AspireCliResourceWatcher { throw failure }

        val exception = assertFailsWith<IOException> { watcher.watchResources().toList() }

        assertEquals(failure.message, exception.message)
        assertSame(failure, ExceptionUtil.getRootCause(exception))
    }

    @Test
    fun `stderr is drained while stdout is waiting for data`() = timeoutRunBlocking {
        val stderrDrained = CompletableDeferred<Unit>()
        val stderr = TestReceiveChannel("warning\n".repeat(2000), atEnd = { stderrDrained.complete(Unit) })
        val stdout = TestReceiveChannel("{\"name\":\"api\",\"resourceType\":\"Project\"}", beforeRead = { stderrDrained.await() })
        val process = TestProcess(stdout, stderr)
        val watcher = AspireCliResourceWatcher { process }

        val updates = watcher.watchResources().toList()

        assertEquals(listOf("api"), updates.map { it.name })
    }

    @Test
    fun `nonzero exit reports exit code and bounded stderr tail`() = timeoutRunBlocking {
        val stderr = TestReceiveChannel("x".repeat(5000) + "\nCLI failed\n")
        val completion = CompletableDeferred(7)
        val process = TestProcess(stderr = stderr, completion = completion)
        val watcher = AspireCliResourceWatcher { process }

        val exception = assertFailsWith<ExecutionException> { watcher.watchResources().toList() }

        val message = exception.message.orEmpty()
        assertTrue(message.contains("code 7"))
        assertTrue(message.endsWith("CLI failed"))
        assertTrue(message.length < 4100)
    }

    @Test
    fun `read failure kills the process and reaches the collector`() = timeoutRunBlocking {
        val failure = IOException("Broken stdout")
        val stdout = TestReceiveChannel("", beforeRead = { throw failure })
        val completion = CompletableDeferred<Int>()
        val process = TestProcess(stdout = stdout, completion = completion)
        val watcher = AspireCliResourceWatcher { process }

        val exception = assertFailsWith<IOException> { watcher.watchResources().toList() }

        assertEquals(failure.message, exception.message)
        assertSame(failure, ExceptionUtil.getRootCause(exception))
        assertTrue(process.killed)
    }

    @Test
    fun `cancelling collection before the first update kills the process`() = timeoutRunBlocking {
        val reading = CompletableDeferred<Unit>()
        val stdout = TestReceiveChannel("", atEnd = {
            reading.complete(Unit)
            awaitCancellation()
        })
        val completion = CompletableDeferred<Int>()
        val process = TestProcess(stdout = stdout, completion = completion)
        val watcher = AspireCliResourceWatcher { process }
        val collection = launch { watcher.watchResources().collect() }
        reading.await()

        collection.cancelAndJoin()

        assertTrue(process.killed)
        assertEquals(137, process.exitCode.await())
    }

    @Test
    fun `taking first update stops the still running process`() = timeoutRunBlocking {
        val stdout = TestReceiveChannel("{\"name\":\"api\",\"resourceType\":\"Project\"}\n", atEnd = { awaitCancellation() })
        val completion = CompletableDeferred<Int>()
        val process = TestProcess(stdout = stdout, completion = completion)
        val watcher = AspireCliResourceWatcher { process }

        val resource = watcher.watchResources().first()

        assertEquals("api", resource.name)
        assertTrue(process.killed)
    }
}
