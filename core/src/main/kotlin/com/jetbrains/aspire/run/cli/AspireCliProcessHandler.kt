@file:Suppress("UnstableApiUsage")

package com.jetbrains.aspire.run.cli

import com.intellij.execution.process.ProcessHandler
import com.intellij.execution.process.ProcessOutputTypes
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.diagnostic.trace
import com.intellij.openapi.util.Key
import com.intellij.platform.eel.EelProcess
import com.intellij.platform.eel.channels.EelReceiveChannel
import com.intellij.platform.eel.provider.utils.lines
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.io.OutputStream
import java.nio.charset.StandardCharsets

internal class AspireCliProcessHandler(
    private val eelProcess: EelProcess,
    private val cs: CoroutineScope,
    private val commandLineText: String
) : ProcessHandler() {
    companion object {
        private val LOG = logger<AspireCliProcessHandler>()
    }

    override fun startNotify() {
        super.startNotify()

        val stdoutJob = cs.launch { pump(eelProcess.stdout, ProcessOutputTypes.STDOUT) }
        val stderrJob = cs.launch { pump(eelProcess.stderr, ProcessOutputTypes.STDERR) }

        cs.launch {
            val exitCode = try {
                eelProcess.exitCode.await()
            } catch (ce: CancellationException) {
                throw ce
            } catch (e: Exception) {
                LOG.trace { "Failed to await exit code of '$commandLineText': ${e.message}" }
                -1
            }

            // Drain the output before reporting termination so nothing is lost.
            stdoutJob.join()
            stderrJob.join()

            notifyProcessTerminated(exitCode)
        }
    }

    private suspend fun pump(channel: EelReceiveChannel, outputType: Key<*>) {
        try {
            channel.lines(StandardCharsets.UTF_8).collect { line ->
                notifyTextAvailable(line + "\n", outputType)
            }
        } catch (ce: CancellationException) {
            throw ce
        } catch (e: Exception) {
            LOG.trace { "Output pump ended for '$commandLineText': ${e.message}" }
        }
    }

    override fun destroyProcessImpl() {
        cs.launch {
            try {
                eelProcess.kill()
            } catch (ce: CancellationException) {
                throw ce
            } catch (e: Exception) {
                LOG.trace { "Failed to kill '$commandLineText': ${e.message}" }
            }
        }
    }

    override fun detachProcessImpl() {
        notifyProcessDetached()
        cs.cancel()
    }

    override fun detachIsDefault(): Boolean = false

    override fun getProcessInput(): OutputStream? = null
}
