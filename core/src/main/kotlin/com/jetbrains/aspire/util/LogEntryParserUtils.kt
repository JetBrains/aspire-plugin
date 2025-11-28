package com.jetbrains.aspire.util

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.trace

private const val YEAR = "(\\d{4})"
private const val MONTH = "(0[1-9]|1[0-2])"
private const val DAY = "(0[1-9]|[12][0-9]|3[01])"
private const val HOUR = "([01][0-9]|2[0-3])"
private const val MINUTES = "([0-5][0-9])"
private const val SECONDS = "([0-5][0-9])"
private const val PARTIAL_SECONDS = "(\\.\\d{1,9})?"
private const val TIME_ZONE = "(Z|([Z+-]([01][0-9]|2[0-3]):([0-5][0-9])))?"

private const val PATTERN = "^$YEAR-$MONTH-${DAY}T$HOUR:$MINUTES:$SECONDS$PARTIAL_SECONDS$TIME_ZONE"
private val regex = Regex(PATTERN)

/**
 * Parses a log entry into a pair of timestamp and log content.
 *
 * The method extracts a timestamp and the log content from the provided log entry string.
 * If the log entry does not conform to the expected format (e.g., missing timestamp), the method returns null.
 *
 * @param logEntry the log entry string to parse
 * @return a pair containing the timestamp and log content, or null if the log entry does not have the expected format
 */
fun parseLogEntry(logEntry: String): Pair<String, String>? {
    val match = regex.find(logEntry) ?: return null

    val timestamp = match.value
    val logContent =
        if (logEntry.length > match.range.last + 2 && logEntry[match.range.last + 1] == ' ')
            logEntry.substring(match.range.last + 2)
        else if (logEntry.length > match.range.last + 1)
            logEntry.substring(match.range.last + 1)
        else ""

    logger.trace { "Received log: $logEntry, resulting $logContent" }
    return timestamp to logContent
}

private val logger = Logger.getInstance("com.jetbrains.aspire.util.LogEntryParserUtilsKt")