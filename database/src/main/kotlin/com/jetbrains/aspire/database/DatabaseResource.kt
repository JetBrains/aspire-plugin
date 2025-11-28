package com.jetbrains.aspire.database

import com.jetbrains.rd.util.lifetime.Lifetime
import java.net.URI

internal data class DatabaseResource(
    val name: String,
    val containerId: String,
    val type: DatabaseType,
    val connectionString: String,
    val urls: List<URI>,
    val containerPorts: String?,
    val isPersistent: Boolean,
    val resourceLifetime: Lifetime
)

internal enum class DatabaseType {
    POSTGRES, MYSQL, MSSQL, ORACLE, MONGO, REDIS
}