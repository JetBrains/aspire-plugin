package com.jetbrains.aspire.database

import java.net.URI

internal data class DatabaseResource(
    val name: String,
    val resourceId: String,
    val containerId: String,
    val type: DatabaseType,
    val connectionString: String,
    val urls: List<URI>,
    val containerPorts: String?,
    val isPersistent: Boolean,
)

internal enum class DatabaseType {
    POSTGRES, MYSQL, MSSQL, ORACLE, MONGO, REDIS
}