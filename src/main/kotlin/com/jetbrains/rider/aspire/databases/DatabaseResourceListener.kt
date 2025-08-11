package com.jetbrains.rider.aspire.databases

import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.diagnostic.trace
import com.intellij.openapi.project.Project
import com.jetbrains.rider.aspire.generated.ResourceState
import com.jetbrains.rider.aspire.generated.ResourceType
import com.jetbrains.rider.aspire.services.AspireResource
import com.jetbrains.rider.aspire.services.ResourceListener
import com.jetbrains.rider.aspire.settings.AspireSettings
import java.net.URI

class DatabaseResourceListener(private val project: Project) : ResourceListener {
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
        if (resource.type != ResourceType.Container) return
        val connectionString = resource.connectionString?.value ?: return

        if (resource.state == ResourceState.Running) {
            val containerId = resource.containerId?.value ?: return
            val resourceType = findDatabaseType(resource.name, resource.containerImage?.value) ?: return
            val urls = resource.urls.mapNotNull { url -> runCatching { URI(url.fullUrl) }.getOrNull() }
            if (urls.isEmpty()) return
            val isPersistent = resource.containerLifetime?.value.equals("persistent", true)

            val databaseResource = DatabaseResource(
                resource.displayName,
                containerId,
                resourceType,
                connectionString,
                urls,
                resource.containerPorts?.value,
                isPersistent,
                resource.lifetime
            )

            LOG.trace { "Created database resource: $databaseResource" }

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