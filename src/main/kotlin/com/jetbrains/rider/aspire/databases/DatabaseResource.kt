package com.jetbrains.rider.aspire.databases

import com.jetbrains.rd.util.lifetime.Lifetime
import java.net.URI

data class DatabaseResource(
    val name: String,
    val containerId: String,
    val type: DatabaseType,
    val connectionString: String,
    val urls: List<URI>,
    val containerPorts: String?,
    val isPersistent: Boolean,
    val resourceLifetime: Lifetime
)

enum class DatabaseType {
    POSTGRES, MYSQL, MSSQL, ORACLE, MONGO, REDIS
}