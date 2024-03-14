package me.rafaelldi.aspire.database

import com.intellij.database.dataSource.LocalDataSource
import com.intellij.database.dataSource.LocalDataSourceManager
import com.intellij.database.util.DbImplUtil
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.util.application
import com.jetbrains.rd.util.lifetime.Lifetime
import com.jetbrains.rider.plugins.appender.database.jdbcToConnectionString.converters.ConnectionStringToJdbcUrlConverter
import com.jetbrains.rider.plugins.appender.database.jdbcToConnectionString.dataProviders.NpgsqlDataProvider
import com.jetbrains.rider.plugins.appender.database.jdbcToConnectionString.factories.ConnectionStringsFactory

@Service(Service.Level.PROJECT)
class DataBaseService(private val project: Project) {
    companion object {
        fun getInstance(project: Project) = project.service<DataBaseService>()
    }

    suspend fun createConnection(connectionName: String, connectionString: String, lifetime: Lifetime) {
        val dataProvider = NpgsqlDataProvider.getInstance(project)
        val factory = ConnectionStringsFactory.get(dataProvider, project) ?: return
        val parsedConnectionString = factory.create(connectionString, dataProvider).getOrNull() ?: return
        val driver = DbImplUtil.guessDatabaseDriver(dataProvider.dbms.first()) ?: return
        val url = ConnectionStringToJdbcUrlConverter.convert(parsedConnectionString, driver, project)
            ?.build()
            ?.getOrNull()
            ?: return

        val dataSource = LocalDataSource.fromDriver(driver, url, true).apply {
            name = connectionName
        }

        val dataSourceManager = LocalDataSourceManager.getInstance(project)
        lifetime.bracketIfAlive({
            application.invokeLater {
                dataSourceManager.addDataSource(dataSource)
            }
        }, {
            application.invokeLater {
                dataSourceManager.removeDataSource(dataSource)
            }
        })
    }
}