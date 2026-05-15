package com.jetbrains.aspire.database

import com.intellij.database.dataSource.DatabaseDriver
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.jetbrains.rider.plugins.appender.database.jdbcToConnectionString.converters.ConnectionStringToJdbcUrlConverter
import com.jetbrains.rider.plugins.appender.database.jdbcToConnectionString.dataProviders.*
import com.jetbrains.rider.plugins.appender.database.jdbcToConnectionString.factories.ConnectionStringsFactory

/**
 * Converts .NET connection strings to JDBC URLs suitable for IntelliJ's database tools.
 *
 * Handles different database types with type-specific strategies:
 * - Redis: custom regex-based parsing to a JDBC Redis URL
 * - MSSQL, MongoDB: connection strings are used as-is (raw)
 * - PostgreSQL, MySQL, Oracle: parsed via [ConnectionStringsFactory] and converted via [ConnectionStringToJdbcUrlConverter]
 */
@Service(Service.Level.PROJECT)
internal class DatabaseConnectionUrlConverter(private val project: Project) {
    companion object {
        fun getInstance(project: Project): DatabaseConnectionUrlConverter = project.service()

        private val LOG = logger<DatabaseConnectionUrlConverter>()

        private const val REDIS_CONNECTION_STRING_PATTERN =
            "(?<host>[\\w.]+):(?<port>\\d+)(?:,(?:user=(?<user>[^,]+)|password=(?<password>[^,]+)|ssl=(?<ssl>[^,]+)|[^,]+))*"
        private val REDIS_REGEX = Regex(REDIS_CONNECTION_STRING_PATTERN)
    }

    private val rawConnectionStringTypes = listOf(DatabaseType.MSSQL, DatabaseType.MONGO)

    fun getDataProvider(type: DatabaseType): DotnetDataProvider =
        when (type) {
            DatabaseType.POSTGRES -> NpgsqlDataProvider.getInstance(project)
            DatabaseType.MYSQL -> MySqlClientDataProvider.getInstance(project)
            DatabaseType.MSSQL -> SqlClientDataProvider.getInstance(project)
            DatabaseType.ORACLE -> OracleClientDataProvider.getInstance(project)
            DatabaseType.MONGO -> DummyMongoDataProvider.getInstance(project)
            DatabaseType.REDIS -> DummyRedisDataProvider.getInstance(project)
        }

    suspend fun getConnectionUrl(
        connectionString: String,
        databaseResource: DatabaseResource,
        dataProvider: DotnetDataProvider,
        driver: DatabaseDriver
    ): String? {
        return try {
            if (databaseResource.type == DatabaseType.REDIS) {
                convertRedisConnectionString(connectionString)
            } else if (rawConnectionStringTypes.contains(databaseResource.type)) {
                connectionString
            } else {
                val factory = ConnectionStringsFactory.get(dataProvider, project)
                if (factory == null) {
                    LOG.warn("Unable to find connection string factory")
                    return null
                }
                val parsedConnectionString =
                    factory.create(connectionString, dataProvider).getOrNull()
                if (parsedConnectionString == null) {
                    LOG.warn("Unable to parse connection string for ${databaseResource.name}")
                    return null
                }
                ConnectionStringToJdbcUrlConverter.convert(parsedConnectionString, driver, project)
                    ?.build()
                    ?.getOrNull()
            }
        } catch (e: Exception) {
            LOG.warn("Failed to get connection URL for ${databaseResource.name}", e)
            return null
        }
    }

    private fun convertRedisConnectionString(connectionString: String): String? {
        val matcher = REDIS_REGEX.toPattern().matcher(connectionString)
        if (!matcher.matches()) return null

        val host = matcher.group("host")
        val port = matcher.group("port")
        val user = matcher.group("user")
        val password = matcher.group("password")
        val ssl = matcher.group("ssl")

        val sb = StringBuilder("jdbc:redis://")
        if (user != null || password != null) {
            user?.let { sb.append(it) }
            if (user != null && password != null) sb.append(":")
            password?.let { sb.append(it) }
            sb.append("@")
        }
        host?.let { sb.append(it) }
        port?.let { sb.append(":").append(it) }
        if (ssl?.lowercase() == "true") sb.append("?ssl=true&verifyServerCertificate=false")

        return sb.toString()
    }
}
