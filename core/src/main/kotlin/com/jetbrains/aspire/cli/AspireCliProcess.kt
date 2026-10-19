@file:Suppress("UnstableApiUsage")

package com.jetbrains.aspire.cli

import com.intellij.diagnostic.rethrowControlFlowException
import com.intellij.openapi.diagnostic.Logger
import com.intellij.platform.eel.EelProcess
import com.intellij.platform.eel.SafeDeferred
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext

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