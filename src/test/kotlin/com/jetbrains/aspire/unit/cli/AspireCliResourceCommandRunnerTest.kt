@file:Suppress("UnstableApiUsage", "NonExtendableApiUsage")

package com.jetbrains.aspire.unit.cli

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
import com.jetbrains.aspire.cli.AspireCliResourceCommandRunner
import com.jetbrains.aspire.resources.AspireResourceCommandRequest
import com.jetbrains.aspire.resources.AspireResourceCommandResponseKind
import com.jetbrains.aspire.resources.AspireResourceCommandResult
import com.jetbrains.aspire.resources.AspireResourceCommandResultFormat
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.yield
import org.junit.jupiter.api.Test
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.nio.ByteBuffer
import kotlin.coroutines.cancellation.CancellationException
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

internal class AspireCliResourceCommandRunnerTest {
    private val appHostPath = "/projects/My App/AppHost.csproj"
    private val request = AspireResourceCommandRequest("api", "Project", "restart")

    @Test
    fun `resource command and apphost are passed as separate CLI arguments`() = timeoutRunBlocking {
        val customRequest = AspireResourceCommandRequest("my resource", "Container", "custom-command")
        var arguments: List<String>? = null
        val runner = AspireCliResourceCommandRunner { args ->
            arguments = args
            TestProcess()
        }
        val expected = listOf("resource", "my resource", "custom-command", "--non-interactive", "--nologo", "--apphost", appHostPath)

        val response = runner.executeResourceCommand(appHostPath, customRequest)

        assertEquals(expected, arguments)
        assertEquals(AspireResourceCommandResponseKind.Succeeded, response.kind)
    }

    @Test
    fun `stdout is returned as text preserving split UTF-8 whitespace and line endings`() = timeoutRunBlocking {
        val output = "  Приложение\r\n{\"value\":42}\nfinal line"
        val process = TestProcess(stdout = TestReceiveChannel(output, chunkSize = 1))
        val runner = AspireCliResourceCommandRunner { process }
        val expectedResult = AspireResourceCommandResult(output, AspireResourceCommandResultFormat.Text, displayImmediately = true)

        val response = runner.executeResourceCommand(appHostPath, request)

        assertEquals(AspireResourceCommandResponseKind.Succeeded, response.kind)
        assertEquals(expectedResult, response.result)
        assertNull(response.message)
        assertFalse(process.killed)
    }

    @Test
    fun `empty stdout produces no result`() = timeoutRunBlocking {
        val runner = AspireCliResourceCommandRunner { TestProcess() }

        val response = runner.executeResourceCommand(appHostPath, request)

        assertEquals(AspireResourceCommandResponseKind.Succeeded, response.kind)
        assertNull(response.result)
    }

    @Test
    fun `whitespace stdout produces no result`() = timeoutRunBlocking {
        val stdout = TestReceiveChannel(" \r\n\t")
        val runner = AspireCliResourceCommandRunner { TestProcess(stdout = stdout) }

        val response = runner.executeResourceCommand(appHostPath, request)

        assertNull(response.result)
    }

    @Test
    fun `stderr is drained concurrently without becoming command output or a failure`() = timeoutRunBlocking {
        val stderrDrained = CompletableDeferred<Unit>()
        val stderr = TestReceiveChannel("Restarting resource\n".repeat(2000), atEnd = { stderrDrained.complete(Unit) })
        val stdout = TestReceiveChannel("command result", beforeRead = { stderrDrained.await() })
        val process = TestProcess(stdout, stderr)
        val runner = AspireCliResourceCommandRunner { process }

        val response = runner.executeResourceCommand(appHostPath, request)

        assertEquals(AspireResourceCommandResponseKind.Succeeded, response.kind)
        assertEquals("command result", response.result?.value)
        assertNull(response.message)
    }

    @Test
    fun `nonzero exit returns a failed response with exit code and bounded stderr tail`() = timeoutRunBlocking {
        val stderr = TestReceiveChannel("x".repeat(5000) + "\nCommand failed\n")
        val process = TestProcess(stderr = stderr, completion = CompletableDeferred(7))
        val runner = AspireCliResourceCommandRunner { process }

        val response = runner.executeResourceCommand(appHostPath, request)

        assertEquals(AspireResourceCommandResponseKind.Failed, response.kind)
        val message = response.message.orEmpty()
        assertTrue(message.contains("aspire resource restart"))
        assertTrue(message.contains("code 7"))
        assertTrue(message.endsWith("Command failed"))
        assertTrue(message.length < 4100)
        assertNull(response.result)
        assertFalse(process.killed)
    }

    @Test
    fun `failed command retains stdout when stderr is empty`() = timeoutRunBlocking {
        val stdout = TestReceiveChannel("failure details")
        val process = TestProcess(stdout = stdout, completion = CompletableDeferred(1))
        val runner = AspireCliResourceCommandRunner { process }

        val response = runner.executeResourceCommand(appHostPath, request)

        assertEquals(AspireResourceCommandResponseKind.Failed, response.kind)
        assertTrue(response.message.orEmpty().contains("code 1"))
        assertEquals("failure details", response.result?.value)
    }

    @Test
    fun `each invocation starts a separate process`() = timeoutRunBlocking {
        var starts = 0
        val runner = AspireCliResourceCommandRunner {
            starts++
            TestProcess()
        }

        runner.executeResourceCommand(appHostPath, request)
        runner.executeResourceCommand(appHostPath, request)

        assertEquals(2, starts)
    }

    @Test
    fun `cancelling a running command kills its process and propagates cancellation`() = timeoutRunBlocking {
        val reading = CompletableDeferred<Unit>()
        val stdout = TestReceiveChannel("", atEnd = {
            reading.complete(Unit)
            awaitCancellation()
        })
        val process = TestProcess(stdout = stdout, completion = CompletableDeferred())
        val runner = AspireCliResourceCommandRunner { process }
        var cancelled = false
        val execution = launch {
            try {
                runner.executeResourceCommand(appHostPath, request)
            } catch (e: CancellationException) {
                cancelled = true
                throw e
            }
        }
        reading.await()

        execution.cancelAndJoin()

        assertTrue(process.killed)
        assertEquals(137, process.exitCode.await())
        assertTrue(cancelled)
    }

    @Test
    fun `cancellation after output streams close still kills the running process`() = timeoutRunBlocking {
        val streamsClosed = CompletableDeferred<Unit>()
        val stderr = TestReceiveChannel("", atEnd = { streamsClosed.complete(Unit) })
        val process = TestProcess(stderr = stderr, completion = CompletableDeferred())
        val runner = AspireCliResourceCommandRunner { process }
        val execution = launch { runner.executeResourceCommand(appHostPath, request) }
        streamsClosed.await()

        execution.cancelAndJoin()

        assertTrue(process.killed)
    }

    @Test
    fun `stdout read failure propagates and kills the process`() = timeoutRunBlocking {
        val failure = IOException("stdout failed")
        val stdout = TestReceiveChannel("", beforeRead = { throw failure })
        val process = TestProcess(stdout = stdout, completion = CompletableDeferred())
        val runner = AspireCliResourceCommandRunner { process }

        val exception = assertFailsWith<IOException> { runner.executeResourceCommand(appHostPath, request) }

        assertSame(failure, ExceptionUtil.getRootCause(exception))
        assertTrue(process.killed)
    }

    @Test
    fun `stderr read failure propagates and kills the process`() = timeoutRunBlocking {
        val failure = IOException("stderr failed")
        val stderr = TestReceiveChannel("", beforeRead = { throw failure })
        val process = TestProcess(stderr = stderr, completion = CompletableDeferred())
        val runner = AspireCliResourceCommandRunner { process }

        val exception = assertFailsWith<IOException> { runner.executeResourceCommand(appHostPath, request) }

        assertSame(failure, ExceptionUtil.getRootCause(exception))
        assertTrue(process.killed)
    }

    @Test
    fun `spawn failure reaches the caller`() = timeoutRunBlocking {
        val failure = IOException("spawn failed")
        val runner = AspireCliResourceCommandRunner { throw failure }

        val exception = assertFailsWith<IOException> { runner.executeResourceCommand(appHostPath, request) }

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

        @Suppress("JetBrainsInternalApiUsage")
        @OptIn(EelDelicateApi::class)
        override fun available(): Int = bytes.size - offset
        override suspend fun closeForReceive() {}
    }
}