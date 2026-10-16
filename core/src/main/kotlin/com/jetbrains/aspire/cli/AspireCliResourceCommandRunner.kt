@file:Suppress("UnstableApiUsage")

package com.jetbrains.aspire.cli

import com.intellij.diagnostic.rethrowControlFlowException
import com.intellij.openapi.diagnostic.logger
import com.intellij.platform.eel.EelProcess
import com.intellij.platform.eel.ReadResult
import com.intellij.platform.eel.SafeDeferred
import com.intellij.platform.eel.channels.EelReceiveChannel
import com.intellij.platform.eel.provider.utils.lines
import com.jetbrains.aspire.resources.AspireResourceCommandRequest
import com.jetbrains.aspire.resources.AspireResourceCommandResponse
import com.jetbrains.aspire.resources.AspireResourceCommandResponseKind
import com.jetbrains.aspire.resources.AspireResourceCommandResult
import com.jetbrains.aspire.resources.AspireResourceCommandResultFormat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.ApiStatus
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer

@ApiStatus.Internal
class AspireCliResourceCommandRunner(private val startProcess: suspend CoroutineScope.(List<String>) -> EelProcess) {
    companion object {
        private val LOG = logger<AspireCliResourceCommandRunner>()
        private const val STDERR_LIMIT = 4000
    }

    @Suppress("JetBrainsInternalApiUsage")
    suspend fun executeResourceCommand(appHostPath: String, request: AspireResourceCommandRequest): AspireResourceCommandResponse = coroutineScope {
        val args = listOf("resource", request.resourceName, request.commandName, "--non-interactive", "--nologo", "--apphost", appHostPath)
        val process = startProcess(args)
        try {
            process.stdin.close(null)
            val stdout = async { process.stdout.readText() }
            val stderr = StringBuilder()
            val stderrJob = launch {
                process.stderr.lines(Charsets.UTF_8).collect { line ->
                    stderr.append(line).append('\n')
                    if (stderr.length > STDERR_LIMIT) {
                        stderr.delete(0, stderr.length - STDERR_LIMIT)
                    }
                }
            }

            val exitCode = process.exitCode.await()
            val output = stdout.await()
            stderrJob.join()
            val result = output.takeIf { it.isNotBlank() }?.let {
                AspireResourceCommandResult(it, AspireResourceCommandResultFormat.Text, displayImmediately = true)
            }
            if (exitCode == 0) {
                AspireResourceCommandResponse(AspireResourceCommandResponseKind.Succeeded, result = result)
            } else {
                val message = "aspire resource ${request.commandName} exited with code $exitCode: ${stderr.toString().trim()}"
                AspireResourceCommandResponse(AspireResourceCommandResponseKind.Failed, message, result)
            }
        } finally {
            withContext(NonCancellable) {
                try {
                    if (process.exitCode.state !is SafeDeferred.State.Completed) {
                        process.kill()
                    }
                } catch (e: Exception) {
                    rethrowControlFlowException(e)
                    LOG.warn("Failed to stop aspire resource process", e)
                }
            }
        }
    }

    private suspend fun EelReceiveChannel.readText(): String {
        val output = ByteArrayOutputStream()
        val buffer = ByteBuffer.allocate(8192)
        do {
            val result = receive(buffer)
            output.write(buffer.array(), 0, buffer.position())
            buffer.clear()
        } while (result != ReadResult.EOF)
        return output.toString(Charsets.UTF_8)
    }
}