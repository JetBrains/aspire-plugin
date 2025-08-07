package com.jetbrains.rider.aspire.databases.mysql

import com.intellij.openapi.project.Project
import com.jetbrains.rider.plugins.appender.database.jdbcToConnectionString.dataProviders.DotnetDataProvider
import com.jetbrains.rider.plugins.appender.database.jdbcToConnectionString.dataProviders.MySqlClientDataProvider
import com.jetbrains.rider.plugins.appender.database.jdbcToConnectionString.factories.ConnectionStringsFactory

class DummyMySqlConnectionStringsFactory(private val project: Project) : ConnectionStringsFactory {
    override fun accepts(dataProvider: DotnetDataProvider) =
        dataProvider is MySqlClientDataProvider

    override suspend fun create(connectionString: String, dataProvider: DotnetDataProvider) =
        DummyMySqlConnectionString.parse(project, connectionString)
}