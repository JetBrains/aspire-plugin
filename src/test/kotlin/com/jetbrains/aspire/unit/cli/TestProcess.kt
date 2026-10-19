@file:Suppress("UnstableApiUsage", "NonExtendableApiUsage")

package com.jetbrains.aspire.unit.cli

import com.intellij.platform.eel.EelApi
import com.intellij.platform.eel.EelPosixProcess
import com.intellij.platform.eel.SafeDeferred
import com.intellij.platform.eel.UnixSignal
import com.intellij.platform.eel.channels.EelReceiveChannel
import com.intellij.platform.eel.provider.utils.asEelChannel
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.yield
import java.io.ByteArrayOutputStream

internal class TestProcess(
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