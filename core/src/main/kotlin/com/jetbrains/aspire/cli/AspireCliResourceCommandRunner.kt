@file:Suppress("UnstableApiUsage")

package com.jetbrains.aspire.cli

import com.intellij.openapi.diagnostic.logger
import com.intellij.platform.eel.EelProcess
import com.intellij.platform.eel.provider.utils.lines
import com.jetbrains.aspire.resources.AspireResourceCommandRequest
import com.jetbrains.aspire.resources.AspireResourceCommandResponse
import com.jetbrains.aspire.resources.AspireResourceCommandResponseKind
import com.jetbrains.aspire.resources.AspireResourceCommandResult
import com.jetbrains.aspire.resources.AspireResourceCommandResultFormat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
class AspireCliResourceCommandRunner(private val startProcess: suspend CoroutineScope.(List<String>) -> EelProcess) {
    companion object {
        private val LOG = logger<AspireCliResourceCommandRunner>()
    }

    @Suppress("JetBrainsInternalApiUsage")
    suspend fun executeResourceCommand(appHostPath: String, request: AspireResourceCommandRequest): AspireResourceCommandResponse = coroutineScope {
        val args = listOf("resource", request.resourceName, request.commandName, "--non-interactive", "--nologo", "--apphost", appHostPath)
        val process = startProcess(args)
        try {
            process.stdin.close(null)

            val stdout = StringBuilder()
            val stdoutJob = launch {
                process.stdout.lines(Charsets.UTF_8).collect { line ->
                    stdout.appendOutput(line)
                }
            }

            val stderr = StringBuilder()
            val stderrJob = launch {
                process.stderr.lines(Charsets.UTF_8).collect { line ->
                    stderr.appendOutput(line)
                }
            }

            val exitCode = process.exitCode.await()
            stdoutJob.join()
            stderrJob.join()
            val result = stdout.toString().takeIf { it.isNotBlank() }?.let {
                AspireResourceCommandResult(it, AspireResourceCommandResultFormat.Text, displayImmediately = true)
            }
            if (exitCode == 0) {
                AspireResourceCommandResponse(AspireResourceCommandResponseKind.Succeeded, result = result)
            } else {
                val message = "aspire resource ${request.commandName} exited with code $exitCode: ${stderr.toString().trim()}"
                AspireResourceCommandResponse(AspireResourceCommandResponseKind.Failed, message, result)
            }
        } finally {
            process.stop("resource", LOG)
        }
    }
}