package com.jetbrains.rider.aspire.util

import kotlinx.datetime.*
import kotlinx.datetime.format.char

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

private val format = LocalDateTime.Format {
    date(LocalDate.Formats.ISO)
    char('T')
    hour(); char(':'); minute(); char(':'); second()
}

fun parseLogEntry(logEntry: String): Pair<String, String>? {
    val match = regex.find(logEntry)
    if (match == null) return null

    val timestamp = match.value
    val logContent =
        if (logEntry.length > match.range.last + 2 && logEntry[match.range.last + 1] == ' ')
            logEntry.substring(match.range.last + 2)
        else if (logEntry.length > match.range.last + 1)
            logEntry.substring(match.range.last + 1)
        else ""

    val instant = Instant.parse(timestamp)
    val dateTime = instant.toLocalDateTime(TimeZone.currentSystemDefault())

    return dateTime.format(format) to logContent
}