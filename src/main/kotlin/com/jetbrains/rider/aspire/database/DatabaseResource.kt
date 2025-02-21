package com.jetbrains.rider.aspire.database

import com.jetbrains.rd.util.lifetime.Lifetime
import java.net.URI

data class DatabaseResource(
    val name: String,
    val containerId: String,
    val type: DatabaseType,
    val urls: List<DatabaseResourceUrl>,
    val isPersistent: Boolean,
    val resourceLifetime: Lifetime
)

enum class DatabaseType {
    POSTGRES, MYSQL, MSSQL, ORACLE, MONGO, REDIS
}

data class DatabaseResourceUrl(val name: String, val uri: URI, val isInternal: Boolean)