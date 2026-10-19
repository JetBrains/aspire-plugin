package com.jetbrains.aspire.unit.cli

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
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EmptySource
import org.junit.jupiter.params.provider.ValueSource
import java.io.IOException
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
        val process = TestProcess()
        val runner = AspireCliResourceCommandRunner { args ->
            arguments = args
            process
        }
        val expected = listOf("resource", "my resource", "custom-command", "--non-interactive", "--nologo", "--apphost", appHostPath)

        val response = runner.executeResourceCommand(appHostPath, customRequest)

        assertEquals(expected, arguments)
        assertEquals(AspireResourceCommandResponseKind.Succeeded, response.kind)
    }

    @ParameterizedTest
    @EmptySource
    @ValueSource(strings = [" \r\n\t"])
    fun `blank stdout produces no result`(output: String) = timeoutRunBlocking {
        val stdout = TestReceiveChannel(output)
        val process = TestProcess(stdout = stdout)
        val runner = AspireCliResourceCommandRunner { process }

        val response = runner.executeResourceCommand(appHostPath, request)

        assertEquals(AspireResourceCommandResponseKind.Succeeded, response.kind)
        assertNull(response.result)
    }

    @Test
    fun `stdout is collected as UTF-8 lines preserving whitespace`() = timeoutRunBlocking {
        val output = "  Application 🚀\r\n{\"value\":42}\nfinal line"
        val stdout = TestReceiveChannel(output, chunkSize = 1)
        val process = TestProcess(stdout = stdout)
        val runner = AspireCliResourceCommandRunner { process }
        val expectedOutput = "  Application 🚀\n{\"value\":42}\nfinal line\n"
        val expectedResult = AspireResourceCommandResult(expectedOutput, AspireResourceCommandResultFormat.Text, displayImmediately = true)

        val response = runner.executeResourceCommand(appHostPath, request)

        assertEquals(AspireResourceCommandResponseKind.Succeeded, response.kind)
        assertEquals(expectedResult, response.result)
        assertNull(response.message)
        assertFalse(process.killed)
    }

    @Test
    fun `stdout retains only the last 4000 characters`() = timeoutRunBlocking {
        val output = "x".repeat(5000) + "\nCommand result"
        val stdout = TestReceiveChannel(output)
        val process = TestProcess(stdout = stdout)
        val runner = AspireCliResourceCommandRunner { process }
        val expectedOutput = "$output\n".takeLast(4000)

        val response = runner.executeResourceCommand(appHostPath, request)

        assertEquals(AspireResourceCommandResponseKind.Succeeded, response.kind)
        assertEquals(expectedOutput, response.result?.value)
    }

    @Test
    fun `nonzero exit returns a failed response with exit code and bounded stderr tail`() = timeoutRunBlocking {
        val stderr = TestReceiveChannel("x".repeat(5000) + "\nCommand failed\n")
        val completion = CompletableDeferred(7)
        val process = TestProcess(stderr = stderr, completion = completion)
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
        val completion = CompletableDeferred(1)
        val process = TestProcess(stdout = stdout, completion = completion)
        val runner = AspireCliResourceCommandRunner { process }

        val response = runner.executeResourceCommand(appHostPath, request)

        assertEquals(AspireResourceCommandResponseKind.Failed, response.kind)
        assertTrue(response.message.orEmpty().contains("code 1"))
        assertEquals("failure details\n", response.result?.value)
    }

    @Test
    fun `spawn failure reaches the caller`() = timeoutRunBlocking {
        val failure = IOException("spawn failed")
        val runner = AspireCliResourceCommandRunner { throw failure }

        val exception = assertFailsWith<IOException> { runner.executeResourceCommand(appHostPath, request) }

        assertSame(failure, ExceptionUtil.getRootCause(exception))
    }

    @ParameterizedTest
    @ValueSource(strings = ["stdout", "stderr"])
    fun `output stream read failure propagates and kills the process`(failedStream: String) = timeoutRunBlocking {
        val failure = IOException("$failedStream failed")
        val channel = TestReceiveChannel("", beforeRead = { throw failure })
        val completion = CompletableDeferred<Int>()
        val process = if (failedStream == "stdout") {
            TestProcess(stdout = channel, completion = completion)
        } else {
            TestProcess(stderr = channel, completion = completion)
        }
        val runner = AspireCliResourceCommandRunner { process }

        val exception = assertFailsWith<IOException> { runner.executeResourceCommand(appHostPath, request) }

        assertSame(failure, ExceptionUtil.getRootCause(exception))
        assertTrue(process.killed)
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
        assertEquals("command result\n", response.result?.value)
        assertNull(response.message)
    }

    @Test
    fun `cancelling a running command kills its process and propagates cancellation`() = timeoutRunBlocking {
        val reading = CompletableDeferred<Unit>()
        val stdout = TestReceiveChannel("", atEnd = {
            reading.complete(Unit)
            awaitCancellation()
        })
        val completion = CompletableDeferred<Int>()
        val process = TestProcess(stdout = stdout, completion = completion)
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
        assertTrue(cancelled)
    }

    @Test
    fun `cancellation after output streams close still kills the running process`() = timeoutRunBlocking {
        val streamsClosed = CompletableDeferred<Unit>()
        val stderr = TestReceiveChannel("", atEnd = { streamsClosed.complete(Unit) })
        val completion = CompletableDeferred<Int>()
        val process = TestProcess(stderr = stderr, completion = completion)
        val runner = AspireCliResourceCommandRunner { process }
        val execution = launch { runner.executeResourceCommand(appHostPath, request) }
        streamsClosed.await()

        execution.cancelAndJoin()

        assertTrue(process.killed)
    }
}