package com.jetbrains.aspire.database

import com.intellij.database.Dbms
import com.intellij.database.dialects.redis.RedisDbms
import java.net.URI

internal data class DatabaseResource(
    val name: String,
    val resourceName: String,
    val containerId: String,
    val type: DatabaseType,
    val connectionString: String,
    val urls: List<URI>,
    val containerPorts: String?,
)

internal enum class DatabaseType {
    POSTGRES, MYSQL, MSSQL, ORACLE, MONGO, REDIS
}

internal fun DatabaseType.getDbms(): Dbms {
    return when (this) {
        DatabaseType.POSTGRES -> Dbms.POSTGRES
        DatabaseType.MYSQL -> Dbms.MYSQL
        DatabaseType.MSSQL -> Dbms.MSSQL
        DatabaseType.ORACLE -> Dbms.ORACLE
        DatabaseType.MONGO -> Dbms.MONGO
        DatabaseType.REDIS -> RedisDbms.REDIS
    }
}