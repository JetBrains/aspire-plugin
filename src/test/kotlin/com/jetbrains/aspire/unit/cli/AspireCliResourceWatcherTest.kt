@file:Suppress("UnstableApiUsage", "NonExtendableApiUsage")

package com.jetbrains.aspire.unit.cli

import com.intellij.execution.ExecutionException
import com.intellij.platform.eel.EelApi
import com.intellij.platform.eel.EelPosixProcess
import com.intellij.platform.eel.ReadResult
import com.intellij.platform.eel.SafeDeferred
import com.intellij.platform.eel.UnixSignal
import com.intellij.platform.eel.channels.EelDelicateApi
import com.intellij.platform.eel.channels.EelReceiveChannel
import com.intellij.platform.eel.provider.utils.asEelChannel
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.util.ExceptionUtil
import com.jetbrains.aspire.cli.AspireCliResourceCommandInputType
import com.jetbrains.aspire.cli.AspireCliResourceWatcher
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.yield
import org.junit.jupiter.api.Test
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.nio.ByteBuffer
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue

internal class AspireCliResourceWatcherTest {
    @Test
    fun `process starts only on collection and each collector gets a new process`() = timeoutRunBlocking {
        var starts = 0
        val watcher = AspireCliResourceWatcher {
            starts++
            TestProcess()
        }
        val updates = watcher.watchResources()

        assertEquals(0, starts)
        assertTrue(updates.toList().isEmpty())
        assertTrue(updates.toList().isEmpty())
        assertEquals(2, starts)
    }

    @Test
    fun `individual resource updates handle split UTF-8 CRLF and final unterminated line`() = timeoutRunBlocking {
        val output = """
            {"name":"api","resourceType":"Project","displayName":"Приложение","state":"Starting"}
            {"name":"api","resourceType":"Project","displayName":"Приложение","state":"Running"}
        """.trimIndent().replace("\n", "\r\n")
        val stdout = TestReceiveChannel(output, chunkSize = 1)
        val process = TestProcess(stdout = stdout)
        val watcher = AspireCliResourceWatcher { process }

        val updates = watcher.watchResources().toList()

        assertEquals(listOf("Starting", "Running"), updates.map { it.state })
        assertTrue(updates.all { it.name == "api" && it.displayName == "Приложение" })
        assertFalse(process.killed)
    }

    @Test
    fun `blank malformed and non-resource lines do not interrupt updates`() = timeoutRunBlocking {
        val output = """

            Aspire CLI
            {invalid json}
            {}
            {"name":"","resourceType":"Project"}
            {"name":"missing-type"}
            {"name":"api","resourceType":"Project","futureField":true}
        """.trimIndent()
        val stdout = TestReceiveChannel(output)
        val process = TestProcess(stdout = stdout)
        val watcher = AspireCliResourceWatcher { process }

        val updates = watcher.watchResources().toList()

        assertEquals(listOf("api"), updates.map { it.name })
    }

    @Test
    fun `nested resource models are deserialized`() = timeoutRunBlocking {
        val output = """
            {"name":"api","resourceType":"Project","healthReports":{"ready":{"status":"Healthy"}},"urls":[{"url":"https://localhost:1234","isInternal":false}],"properties":{"pid":"123","optional":null},"commands":{"restart":{"argumentInputs":[{"name":"force","inputType":"Boolean","required":true}]}}}
        """.trimIndent()
        val stdout = TestReceiveChannel(output)
        val process = TestProcess(stdout = stdout)
        val watcher = AspireCliResourceWatcher { process }

        val resource = watcher.watchResources().toList().single()

        assertEquals("Healthy", resource.healthReports?.get("ready")?.status)
        assertEquals("https://localhost:1234", resource.urls?.single()?.url)
        assertEquals(mapOf("pid" to "123", "optional" to null), resource.properties)
        val argument = resource.commands?.get("restart")?.argumentInputs?.single()
        assertEquals(AspireCliResourceCommandInputType.Boolean, argument?.inputType)
        assertEquals(true, argument?.required)
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
        val process = TestProcess(stderr = stderr, completion = CompletableDeferred(7))
        val watcher = AspireCliResourceWatcher { process }

        val exception = assertFailsWith<ExecutionException> { watcher.watchResources().toList() }

        val message = exception.message.orEmpty()
        assertTrue(message.contains("code 7"))
        assertTrue(message.endsWith("CLI failed"))
        assertTrue(message.length < 4100)
    }

    @Test
    fun `cancelling collection before the first update kills the process`() = timeoutRunBlocking {
        val reading = CompletableDeferred<Unit>()
        val stdout = TestReceiveChannel("", atEnd = {
            reading.complete(Unit)
            awaitCancellation()
        })
        val process = TestProcess(stdout = stdout, completion = CompletableDeferred())
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
        val process = TestProcess(stdout = stdout, completion = CompletableDeferred())
        val watcher = AspireCliResourceWatcher { process }

        val resource = watcher.watchResources().first()

        assertEquals("api", resource.name)
        assertTrue(process.killed)
    }

    @Test
    fun `read failure kills the process and reaches the collector`() = timeoutRunBlocking {
        val failure = IOException("Broken stdout")
        val stdout = TestReceiveChannel("", beforeRead = { throw failure })
        val process = TestProcess(stdout = stdout, completion = CompletableDeferred())
        val watcher = AspireCliResourceWatcher { process }

        val exception = assertFailsWith<IOException> { watcher.watchResources().toList() }

        assertEquals(failure.message, exception.message)
        assertSame(failure, ExceptionUtil.getRootCause(exception))
        assertTrue(process.killed)
    }

    @Test
    fun `spawn failure reaches the collector`() = timeoutRunBlocking {
        val failure = IOException("CLI unavailable")
        val watcher = AspireCliResourceWatcher { throw failure }

        val exception = assertFailsWith<IOException> { watcher.watchResources().toList() }

        assertEquals(failure.message, exception.message)
        assertSame(failure, ExceptionUtil.getRootCause(exception))
    }

    private class TestProcess(
        override val stdout: EelReceiveChannel = TestReceiveChannel(""),
        override val stderr: EelReceiveChannel = TestReceiveChannel(""),
        private val completion: CompletableDeferred<Int> = CompletableDeferred(0),
    ) : EelPosixProcess {
        override val pid = object : EelApi.Pid {
            override val value: Long = 123
        }
        override val stdin = ByteArrayOutputStream().asEelChannel()
        override val exitCode = SafeDeferred(completion)
        var killed = false
            private set

        override suspend fun kill() {
            yield()
            killed = true
            completion.complete(137)
        }

        override suspend fun interrupt() = error("Unexpected interrupt")
        override suspend fun sendSignal(sig: UnixSignal) = error("Unexpected signal")
        override suspend fun resizePty(columns: Int, rows: Int) = error("Unexpected PTY resize")
    }

    private class TestReceiveChannel(
        text: String,
        private val chunkSize: Int = Int.MAX_VALUE,
        private val beforeRead: suspend () -> Unit = {},
        private val atEnd: suspend () -> Unit = {},
    ) : EelReceiveChannel {
        private val bytes = text.toByteArray(Charsets.UTF_8)
        private var offset = 0
        override val prefersDirectBuffers: Boolean = false

        override suspend fun receive(dst: ByteBuffer): ReadResult {
            beforeRead()
            if (offset == bytes.size) {
                atEnd()
                return ReadResult.EOF
            }
            val size = minOf(dst.remaining(), bytes.size - offset, chunkSize)
            dst.put(bytes, offset, size)
            offset += size
            return ReadResult.NOT_EOF
        }

        @OptIn(EelDelicateApi::class)
        override fun available(): Int = bytes.size - offset
        override suspend fun closeForReceive() {}
    }
}