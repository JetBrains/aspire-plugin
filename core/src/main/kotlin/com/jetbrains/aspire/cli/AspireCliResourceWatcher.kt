@file:Suppress("UnstableApiUsage")

package com.jetbrains.aspire.cli

import com.intellij.diagnostic.rethrowControlFlowException
import com.intellij.execution.ExecutionException
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.diagnostic.trace
import com.intellij.platform.eel.EelProcess
import com.intellij.platform.eel.SafeDeferred
import com.intellij.platform.eel.provider.utils.lines
import com.jetbrains.aspire.worker.AspireResourceLogEntry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
class AspireCliResourceWatcher(private val startProcess: suspend CoroutineScope.() -> EelProcess) {
    companion object {
        private val LOG = logger<AspireCliResourceWatcher>()
        private val JSON = Json { ignoreUnknownKeys = true }
        private const val STDERR_LIMIT = 4000
    }

    fun watchResources(): Flow<AspireCliResource> = watch("describe", ::parseResource)

    fun watchResourceConsoleLogs(): Flow<List<AspireResourceLogEntry>> = watch("logs") { line ->
        parseLine<AspireCliLogLine>(line, "logs")
            ?.takeIf { it.content.isNotEmpty() }
            ?.let { listOf(AspireResourceLogEntry(it.content, it.isError)) }
    }

    @Suppress("JetBrainsInternalApiUsage")
    private fun <T> watch(command: String, parse: (String) -> T?): Flow<T> = channelFlow {
        val process = startProcess()
        try {
            process.stdin.close(null)
            val stderr = StringBuilder()
            val stderrJob = launch {
                process.stderr.lines(Charsets.UTF_8).collect { line ->
                    stderr.append(line).append('\n')
                    if (stderr.length > STDERR_LIMIT) {
                        stderr.delete(0, stderr.length - STDERR_LIMIT)
                    }
                    LOG.trace { "aspire $command stderr: $line" }
                }
            }

            process.stdout.lines(Charsets.UTF_8).collect { line ->
                parse(line)?.let { send(it) }
            }
            val exitCode = process.exitCode.await()
            stderrJob.join()
            if (exitCode != 0) {
                throw ExecutionException("aspire $command --follow exited with code $exitCode: ${stderr.toString().trim()}")
            }
        } finally {
            withContext(NonCancellable) {
                try {
                    if (process.exitCode.state !is SafeDeferred.State.Completed) {
                        process.kill()
                    }
                } catch (e: Exception) {
                    rethrowControlFlowException(e)
                    LOG.warn("Failed to stop aspire $command process", e)
                }
            }
        }
    }

    private fun parseResource(line: String): AspireCliResource? =
        parseLine<AspireCliResource>(line, "describe")?.takeIf { it.name.isNotBlank() }

    private inline fun <reified T> parseLine(line: String, command: String): T? {
        val trimmed = line.trim()
        if (!trimmed.startsWith('{')) return null

        return try {
            JSON.decodeFromString<T>(trimmed)
        } catch (e: SerializationException) {
            LOG.trace { "Unable to parse aspire $command update: ${e.message}" }
            null
        }
    }
}