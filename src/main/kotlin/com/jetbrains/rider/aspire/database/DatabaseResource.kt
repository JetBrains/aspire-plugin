package com.jetbrains.rider.aspire.database

import com.jetbrains.rd.util.lifetime.Lifetime
import java.net.URI

data class DatabaseResource(
    val name: String,
    val containerId: String,
    val type: DatabaseType,
    val ports: List<String>,
    val urls: List<URI>,
    val isPersistent: Boolean,
    val lifetime: Lifetime
)

data class DatabaseResourceConnectionString(
    val name: String,
    val connectionString: String,
    val lifetime: Lifetime
)

enum class DatabaseType {
    POSTGRES, MYSQL, MSSQL, ORACLE, MONGO, REDIS
}