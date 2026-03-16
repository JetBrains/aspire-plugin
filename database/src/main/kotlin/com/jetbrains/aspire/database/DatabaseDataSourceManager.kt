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

    private val registeredResources = ConcurrentHashMap<String, Unit>()

    fun tryRegisterResourceId(resourceId: String): Boolean {
        return registeredResources.putIfAbsent(resourceId, Unit) == null
    }

    fun unregisterResourceId(resourceId: String) {
        registeredResources.remove(resourceId)
    }

    suspend fun createDataSource(
        databaseResource: DatabaseResource,
        driver: DatabaseDriver,
        url: String
    ): LocalDataSource? {
        val dataSourceManager = LocalDataSourceManager.getInstance(project)
        if (dataSourceManager.dataSources.any { it.url == url }) {
            LOG.trace { "Data source with the same url as for ${databaseResource.name} is already in use" }
            return null
        }

        val dataSourceForResource = dataSourceManager.dataSources
            .singleOrNull { it.getAdditionalProperty(ASPIRE_RESOURCE_ID) == databaseResource.resourceId }
        if (dataSourceForResource != null) {
            LOG.trace { "Removing existing data source for ${databaseResource.name} (${databaseResource.resourceId})" }
            withContext(Dispatchers.EDT) {
                dataSourceManager.removeDataSource(dataSourceForResource)
            }
        }

        LOG.trace { "Creating a new data source for ${databaseResource.name} (${databaseResource.resourceId})" }
        val createdDataSource = LocalDataSource.fromDriver(driver, url, true).apply {
            name = databaseResource.name
            isAutoSynchronize = true
            setAdditionalProperty(ASPIRE_RESOURCE_ID, databaseResource.resourceId)
        }
        withContext(Dispatchers.EDT) {
            dataSourceManager.addDataSource(createdDataSource)
        }

        return createdDataSource
    }

    suspend fun removeDataSource(resourceId: String) {
        val dataSourceManager = LocalDataSourceManager.getInstance(project)
        val dataSource = dataSourceManager.dataSources
            .singleOrNull { it.getAdditionalProperty(ASPIRE_RESOURCE_ID) == resourceId }
            ?: return

        LOG.trace { "Removing data source for resource $resourceId" }

        withContext(Dispatchers.EDT) {
            dataSourceManager.removeDataSource(dataSource)
        }
    }

    class DataSourceListener(private val project: Project) : DataSourceStorage.Listener {
        override fun dataSourceRemoved(dataSource: LocalDataSource) {
            val resourceId = dataSource.getAdditionalProperty(ASPIRE_RESOURCE_ID) ?: return
            getInstance(project).unregisterResourceId(resourceId)
        }
    }
}
