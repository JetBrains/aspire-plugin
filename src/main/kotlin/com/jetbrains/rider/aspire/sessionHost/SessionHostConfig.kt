package com.jetbrains.rider.aspire.sessionHost

data class SessionHostConfig(
    val rdPort: Int,
    val debugSessionToken: String,
    val debugSessionPort: Int
)
