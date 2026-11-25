@file:Suppress("UnstableApiUsage")

package com.jetbrains.aspire.cli

import com.intellij.diagnostic.rethrowControlFlowException
import com.intellij.openapi.diagnostic.Logger
import com.intellij.platform.eel.EelProcess
import com.intellij.platform.eel.ReadResult
import com.intellij.platform.eel.SafeDeferred
import com.intellij.platform.eel.channels.EelReceiveChannel
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.nio.ByteBuffer

private const val OUTPUT_LIMIT = 4000

internal fun StringBuilder.appendOutput(line: String) {
    append(line).append('\n')
    if (length > OUTPUT_LIMIT) {
        delete(0, length - OUTPUT_LIMIT)
    }
}

internal suspend fun EelProcess.stop(command: String, logger: Logger) = withContext(NonCancellable) {
    try {
        if (exitCode.state !is SafeDeferred.State.Completed) {
            kill()
        }
    } catch (e: Exception) {
        rethrowControlFlowException(e)
        logger.warn("Failed to stop aspire $command process", e)
    }
}

/**
 * Reads the channel to the end as UTF-8 text.
 *
 * @throws IOException if the channel has more than [maxBytes] bytes
 */
internal suspend fun EelReceiveChannel.readLimitedText(maxBytes: Int): String {
    val accumulatedBytes = ByteArrayOutputStream()
    val readBuffer = ByteBuffer.allocate(8192)
    do {
        readBuffer.clear()
        val readStatus = receive(readBuffer)
        if (accumulatedBytes.size() + readBuffer.position() > maxBytes) {
            throw IOException("Aspire CLI output exceeded $maxBytes bytes")
        }
        accumulatedBytes.write(readBuffer.array(), 0, readBuffer.position())
    } while (readStatus != ReadResult.EOF)

    return accumulatedBytes.toString(Charsets.UTF_8)
}