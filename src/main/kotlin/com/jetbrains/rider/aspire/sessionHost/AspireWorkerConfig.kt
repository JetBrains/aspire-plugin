package com.jetbrains.rider.aspire.sessionHost

data class AspireWorkerConfig(
    val rdPort: Int,
    val debugSessionToken: String,
    val debugSessionPort: Int
)
