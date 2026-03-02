package com.jetbrains.aspire.database

import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.diagnostic.trace
import com.intellij.openapi.project.Project
import com.jetbrains.aspire.worker.AspireResource
import com.jetbrains.aspire.dashboard.ResourceListener
import com.jetbrains.aspire.generated.ResourceState
import com.jetbrains.aspire.generated.ResourceType
import com.jetbrains.aspire.settings.AspireSettings
import java.net.URI

internal class DatabaseResourceListener(private val project: Project) : ResourceListener {
    companion object {
        private val LOG = logger<DatabaseResourceListener>()

        private const val POSTGRES = "postgres"
        private const val MYSQL = "mysql"
        private const val MSSQL = "mssql"
        private const val ORACLE = "oracle"
        private const val MONGO = "mongo"
        private const val REDIS = "redis"
    }

    override fun resourceCreated(resource: AspireResource) {
        applyChanges(resource)
    }

    override fun resourceUpdated(resource: AspireResource) {
        applyChanges(resource)
    }

    private fun applyChanges(resource: AspireResource) {
        if (!AspireSettings.getInstance().connectToDatabase) return
        val data = resource.resourceState.value
        if (data.type != ResourceType.Container) return
        val connectionString = data.connectionString?.value ?: return

        if (data.state == ResourceState.Running) {
            val containerId = data.containerId?.value ?: return
            val resourceType = findDatabaseType(data.name, data.containerImage?.value) ?: return
            val urls = data.urls.mapNotNull { url -> runCatching { URI(url.fullUrl) }.getOrNull() }
            if (urls.isEmpty()) return
            val isPersistent = data.containerLifetime?.value.equals("persistent", true)

            val databaseResource = DatabaseResource(
                data.displayName,
                resource.resourceId,
                containerId,
                resourceType,
                connectionString,
                urls,
                data.containerPorts?.value,
                isPersistent,
            )

            LOG.trace { "Created database resource: ${databaseResource.name}" }

            val command = DatabaseResourceConnectionService.AddDatabaseResourceConnection(databaseResource)
            DatabaseResourceConnectionService.getInstance(project).sendConnectionCommand(command)
        }
    }

    private fun findDatabaseType(resourceName: String, resourceImage: String?): DatabaseType? {
        if (resourceImage != null) {
            val databaseTypeByImage = findDatabaseType(resourceImage)
            if (databaseTypeByImage != null) {
                return databaseTypeByImage
            }
        }

        return if (AspireSettings.getInstance().checkResourceNameForDatabase) findDatabaseType(resourceName)
        else null
    }

    private fun findDatabaseType(value: String): DatabaseType? {
        if (value.contains(POSTGRES)) return DatabaseType.POSTGRES
        if (value.contains(MYSQL)) return DatabaseType.MYSQL
        if (value.contains(MSSQL)) return DatabaseType.MSSQL
        if (value.contains(ORACLE)) return DatabaseType.ORACLE
        if (value.contains(MONGO)) return DatabaseType.MONGO
        if (value.contains(REDIS)) return DatabaseType.REDIS
        return null
    }
}