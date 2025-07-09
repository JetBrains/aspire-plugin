package com.jetbrains.rider.aspire.database

import com.intellij.openapi.project.Project
import com.jetbrains.rider.aspire.generated.ResourceType
import com.jetbrains.rider.aspire.services.AspireResource
import com.jetbrains.rider.aspire.services.ResourceListener
import com.jetbrains.rider.aspire.settings.AspireSettings
import java.net.URI

class DatabaseResourceListener(private val project: Project) : ResourceListener {
    companion object {
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

        if (resource.type == ResourceType.Container) {
            val containerId = resource.containerId ?: return
            val resourceType = getType(resource.name, resource.containerImage) ?: return
            val urls = resource.urls
                .mapNotNull { url ->
                    runCatching { URI(url.fullUrl) }.getOrNull()
                        ?.let { DatabaseResourceUrl(url.endpointName ?: "", it, url.isInternal) }
                }
            if (urls.isEmpty()) return
            val isPersistent = resource.containerLifetime.equals("persistent", true)

            val databaseResource = DatabaseResource(
                resource.name,
                containerId,
                resourceType,
                urls,
                resource.containerPorts,
                isPersistent,
                resource.lifetime
            )
            ResourceDatabaseService.getInstance(project).put(databaseResource)
        }
    }

    private fun getType(resourceName: String, image: String?): DatabaseType? {
        if (image != null) {
            val databaseTypeByImage = findDatabaseType(image)
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