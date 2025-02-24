package com.jetbrains.rider.aspire.database

import com.intellij.openapi.project.Project
import com.jetbrains.rider.aspire.generated.ResourceType
import com.jetbrains.rider.aspire.services.AspireResource
import com.jetbrains.rider.aspire.services.ResourceListener
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
        if (resource.type == ResourceType.Container) {
            val containerId = resource.containerId ?: return
            val resourceType = getType(resource.containerImage) ?: return
            val urls = resource.urls
                .mapNotNull { url ->
                    runCatching { URI(url.fullUrl) }.getOrNull()
                        ?.let { DatabaseResourceUrl(url.name, it, url.isInternal) }
                }
            if (urls.isEmpty()) return
            val isPersistent = resource.containerLifetime.equals("persistent", true)

            val databaseResource = DatabaseResource(
                resource.name,
                containerId,
                resourceType,
                urls,
                isPersistent,
                resource.lifetime
            )
            ResourceDatabaseService.getInstance(project).put(databaseResource)
        }
    }

    private fun getType(image: String?): DatabaseType? {
        if (image == null) return null
        if (image.contains(POSTGRES)) return DatabaseType.POSTGRES
        if (image.contains(MYSQL)) return DatabaseType.MYSQL
        if (image.contains(MSSQL)) return DatabaseType.MSSQL
        if (image.contains(ORACLE)) return DatabaseType.ORACLE
        if (image.contains(MONGO)) return DatabaseType.MONGO
        if (image.contains(REDIS)) return DatabaseType.REDIS
        return null
    }
}