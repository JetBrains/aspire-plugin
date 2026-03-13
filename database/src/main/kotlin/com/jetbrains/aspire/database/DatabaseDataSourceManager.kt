package com.jetbrains.aspire.database

import com.intellij.database.dataSource.DataSourceStorage
import com.intellij.database.dataSource.DatabaseDriver
import com.intellij.database.dataSource.LocalDataSource
import com.intellij.database.dataSource.LocalDataSourceManager
import com.intellij.openapi.application.EDT
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.diagnostic.trace
import com.intellij.openapi.project.Project
import com.intellij.util.application
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap

/**
 * Manages the lifecycle of [LocalDataSource] instances created for Aspire database resources.
 *
 * Handles creation, deduplication, replacement (when a resource's URL changes),
 * and cleanup of data sources. For non-persistent resources or unmodified connection strings,
 * data sources are automatically removed when the resource lifetime ends.
 */
@Service(Service.Level.PROJECT)
internal class DatabaseDataSourceManager(private val project: Project) {
    companion object {
        fun getInstance(project: Project): DatabaseDataSourceManager = project.service()

        private val LOG = logger<DatabaseDataSourceManager>()
        private const val ASPIRE_RESOURCE_ID = "aspireResourceId"
    }

    private val connectionStrings = ConcurrentHashMap<String, Unit>()
    private val urlToConnectionString = ConcurrentHashMap<String, String>()

    fun tryRegisterConnectionString(connectionString: String): Boolean {
        return connectionStrings.putIfAbsent(connectionString, Unit) == null
    }

    fun unregisterConnectionString(connectionString: String) {
        connectionStrings.remove(connectionString)
    }

    fun removeConnectionStringByUrl(url: String?) {
        if (url == null) return
        val connectionString = urlToConnectionString.remove(url) ?: return
        connectionStrings.remove(connectionString)
    }

    suspend fun createDataSource(
        connectionString: String,
        connectionStringWasModified: Boolean,
        databaseResource: DatabaseResource,
        driver: DatabaseDriver,
        url: String
    ): LocalDataSource? {
        urlToConnectionString[url] = connectionString

        val dataSourceManager = LocalDataSourceManager.getInstance(project)
        if (dataSourceManager.dataSources.any { it.url == url }) {
            LOG.trace { "Data source for ${databaseResource.name} is already in use" }
            return null
        }

        dataSourceManager.dataSources
            .singleOrNull { it.getAdditionalProperty(ASPIRE_RESOURCE_ID) == databaseResource.resourceId }
            ?.let {
                LOG.trace { "Replacing data source for ${databaseResource.name} (${databaseResource.resourceId})" }
                withContext(Dispatchers.EDT) {
                    dataSourceManager.removeDataSource(it)
                }
            }

        LOG.trace { "Creating a new data source for ${databaseResource.name}" }
        val createdDataSource = LocalDataSource.fromDriver(driver, url, true).apply {
            name = databaseResource.name
            isAutoSynchronize = true
            setAdditionalProperty(ASPIRE_RESOURCE_ID, databaseResource.resourceId)
        }
        withContext(Dispatchers.EDT) {
            dataSourceManager.addDataSource(createdDataSource)
        }

        if (!connectionStringWasModified || !databaseResource.isPersistent) {
            databaseResource.resourceLifetime.onTerminationIfAlive {
                LOG.trace { "Removing data source for ${databaseResource.name}" }
                application.invokeLater {
                    dataSourceManager.removeDataSource(createdDataSource)
                }
            }
        }

        return createdDataSource
    }

    class DataSourceListener(private val project: Project) : DataSourceStorage.Listener {
        override fun dataSourceRemoved(dataSource: LocalDataSource) {
            getInstance(project).removeConnectionStringByUrl(dataSource.url)
        }
    }
}
