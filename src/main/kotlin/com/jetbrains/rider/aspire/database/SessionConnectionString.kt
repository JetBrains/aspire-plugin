package com.jetbrains.rider.aspire.database

import com.jetbrains.rd.util.lifetime.Lifetime

data class SessionConnectionString(
    val sessionId: String,
    val connectionName: String,
    val connectionString: String,
    val sessionLifetime: Lifetime
)