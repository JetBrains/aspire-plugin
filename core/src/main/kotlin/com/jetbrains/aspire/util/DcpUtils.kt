package com.jetbrains.aspire.util

fun generateDcpInstancePrefix(): String {
    val allowedChars = ('A'..'Z') + ('a'..'z') + ('0'..'9')
    return (1..5)
        .map { allowedChars.random() }
        .joinToString("")
}