@file:Suppress("UnstableApiUsage")

package com.jetbrains.aspire.cli

import com.intellij.execution.ExecutionException
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.diagnostic.trace
import com.intellij.platform.eel.EelProcess
import com.intellij.platform.eel.provider.utils.lines
import com.jetbrains.aspire.worker.AspireResourceLogEntry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import org.jetbrains.annotations.ApiStatus
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource

@ApiStatus.Internal
class AspireCliResourceWatcher(
    private val timeSource: TimeSource = TimeSource.Monotonic,
    private val startProcess: suspend CoroutineScope.() -> EelProcess,
) {
    companion object {
        private val LOG = logger<AspireCliResourceWatcher>()
        private val JSON = Json { ignoreUnknownKeys = true }
        private val STARTUP_RETRY_TIMEOUT = 30.seconds
        private val INITIAL_RETRY_DELAY = 100.milliseconds
        private val MAX_RETRY_DELAY = 2.seconds
    }

    fun watchResources(): Flow<AspireCliResource> = watch("describe", ::parseResource)

    fun watchResourceConsoleLogs(): Flow<List<AspireResourceLogEntry>> = watch("logs") { line ->
        parseLine<AspireCliLogLine>(line, "logs")
            ?.takeIf { it.content.isNotEmpty() }
            ?.let { listOf(AspireResourceLogEntry(it.content, it.isError)) }
    }

    private fun <T> watch(command: String, parse: (String) -> T?): Flow<T> = channelFlow {
        val started = timeSource.markNow()
        var retryDelay = INITIAL_RETRY_DELAY
        while (true) {
            val result = watchProcess(command, parse) { send(it) }
            if (result.receivedEvent) return@channelFlow

            val remaining = STARTUP_RETRY_TIMEOUT - started.elapsedNow()
            if (remaining > Duration.ZERO) {
                val wait = minOf(retryDelay, remaining)
                LOG.trace { "aspire $command --follow exited without events; retrying in $wait" }
                delay(wait)
            }
            if (started.elapsedNow() >= STARTUP_RETRY_TIMEOUT) {
                throw ExecutionException(
                    "aspire $command --follow exited with code 0 without any events; " +
                    "startup retry timeout after ${started.elapsedNow()}. " +
                    "stderr: ${result.stderr.trim()}\nstdout: ${result.stdout.trim()}"
                )
            }
            retryDelay = minOf(retryDelay * 2, MAX_RETRY_DELAY)
        }
    }

    @Suppress("JetBrainsInternalApiUsage")
    private suspend fun <T> watchProcess(
        command: String,
        parse: (String) -> T?,
        onEvent: suspend (T) -> Unit,
    ): WatchResult = coroutineScope {
        var receivedEvent = false
        val process = startProcess()

        try {
            process.stdin.close(null)

            val stderr = StringBuilder()
            val stderrJob = launch {
                process.stderr.lines(Charsets.UTF_8).collect { line ->
                    stderr.appendOutput(line)
                    LOG.trace { "aspire $command stderr: $line" }
                }
            }

            val stdout = StringBuilder()
            val stdoutJob = launch {
                process.stdout.lines(Charsets.UTF_8).collect { line ->
                    val event = parse(line)
                    if (event != null) {
                        receivedEvent = true
                        onEvent(event)
                    } else {
                        stdout.appendOutput(line)
                    }
                }
            }

            val exitCode = process.exitCode.await()
            stdoutJob.join()
            stderrJob.join()
            if (exitCode != 0) {
                throw ExecutionException("aspire $command --follow exited with code $exitCode: ${stderr.toString().trim()}")
            }

            WatchResult(receivedEvent, stderr.toString(), stdout.toString())
        } finally {
            process.stop(command, LOG)
        }
    }

    private data class WatchResult(val receivedEvent: Boolean, val stderr: String, val stdout: String)

    private fun parseResource(line: String): AspireCliResource? =
        parseLine<AspireCliResource>(line, "describe")?.takeIf { !it.name.isNullOrBlank() }

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
