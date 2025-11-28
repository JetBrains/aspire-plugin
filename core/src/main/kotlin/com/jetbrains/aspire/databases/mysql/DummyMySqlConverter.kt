package com.jetbrains.aspire.databases.mysql

import com.intellij.database.dataSource.DatabaseDriver
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.text.StringUtil
import com.jetbrains.rider.plugins.appender.database.jdbcToConnectionString.dataProviders.DotnetDataProvider
import com.jetbrains.rider.plugins.appender.database.jdbcToConnectionString.shared.connectionStrings.ConnectionString
import com.jetbrains.rider.plugins.appender.database.jdbcToConnectionString.shared.converters.ConverterBase
import com.jetbrains.rider.plugins.appender.database.jdbcToConnectionString.shared.jdbcUrls.JdbcUrl

internal class DummyMySqlConverter(@Suppress("unused") private val project: Project) :
    ConverterBase<DummyMySqlConnectionString, DummyMySqlJdbcUrl>() {

    init {
        directMap(DummyMySqlConnectionString.Property.Server, DummyMySqlJdbcUrl.Property.Server)
        directMap(DummyMySqlConnectionString.Property.Port, DummyMySqlJdbcUrl.Property.Port)
        directMap(DummyMySqlConnectionString.Property.Database, DummyMySqlJdbcUrl.Property.Database)
        directMap(DummyMySqlConnectionString.Property.Username, DummyMySqlJdbcUrl.Property.User)
        directMap(DummyMySqlConnectionString.Property.Password, DummyMySqlJdbcUrl.Property.Password)
    }

    override fun accepts(connectionString: ConnectionString, databaseDriver: DatabaseDriver) =
        getConnectionStringIfAccepts(connectionString, databaseDriver) != null

    override fun convert(connectionString: ConnectionString, databaseDriver: DatabaseDriver): JdbcUrl? {
        val mySqlConnectionString = getConnectionStringIfAccepts(connectionString, databaseDriver) ?: return null
        return toJdbcUrl(mySqlConnectionString, DummyMySqlJdbcUrl())
    }

    private fun getConnectionStringIfAccepts(
        connectionString: ConnectionString,
        databaseDriver: DatabaseDriver
    ) = if (connectionString is DummyMySqlConnectionString && isMySqlDriver(databaseDriver)) connectionString else null

    private fun isMySqlDriver(driver: DatabaseDriver): Boolean {
        val driverClass = driver.driverClass ?: return false
        return StringUtil.containsIgnoreCase(driverClass, "com.mysql")
    }

    override fun accepts(jdbcUrl: JdbcUrl, provider: DotnetDataProvider): Boolean {
        TODO("Not yet implemented")
    }

    override fun convert(jdbcUrl: JdbcUrl, provider: DotnetDataProvider): ConnectionString? {
        TODO("Not yet implemented")
    }
}