package me.rafaelldi.aspire.database

import com.jetbrains.rd.util.lifetime.Lifetime

data class DatabaseResource(
    val name: String,
    val type: DatabaseResourceType,
    val ports: List<String>,
    val lifetime: Lifetime
)

data class DatabaseResourceConnectionString(
    val name: String,
    val connectionString: String,
    val lifetime: Lifetime
)

enum class DatabaseResourceType {
    POSTGRES, MYSQL, MSSQL, ORACLE, MONGO
}