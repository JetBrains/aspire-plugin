package com.jetbrains.rider.aspire.worker

data class AspireWorkerConfig(
    val rdPort: Int,
    val debugSessionToken: String,
    val debugSessionPort: Int,
    val useHttps: Boolean,
)