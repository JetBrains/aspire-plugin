package me.rafaelldi.aspire.util

import com.intellij.execution.process.AnsiEscapeDecoder
import com.intellij.openapi.util.Key

private val ansiEscapeDecoder by lazy { AnsiEscapeDecoder() }

fun decodeAnsiCommandsToString(ansi: String, outputType: Key<*>): String {
    val buffer = StringBuilder()
    ansiEscapeDecoder.escapeText(ansi, outputType) { text, _ ->
        buffer.append(text)
    }
    return buffer.toString()
}