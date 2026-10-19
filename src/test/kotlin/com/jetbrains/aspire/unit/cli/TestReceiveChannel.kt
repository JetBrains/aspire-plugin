@file:Suppress("UnstableApiUsage", "NonExtendableApiUsage")

package com.jetbrains.aspire.unit.cli

import com.intellij.platform.eel.ReadResult
import com.intellij.platform.eel.channels.EelDelicateApi
import com.intellij.platform.eel.channels.EelReceiveChannel
import java.nio.ByteBuffer

internal class TestReceiveChannel(
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