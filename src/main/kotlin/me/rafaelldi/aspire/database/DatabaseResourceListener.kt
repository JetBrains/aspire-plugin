package me.rafaelldi.aspire.database

import com.intellij.openapi.project.Project
import me.rafaelldi.aspire.generated.ResourceType
import me.rafaelldi.aspire.services.AspireResourceService
import me.rafaelldi.aspire.services.ResourceListener

class DatabaseResourceListener(private val project: Project) : ResourceListener {
    companion object {
        private const val CONNECTION_STRINGS = "ConnectionStrings"
        private const val CONNECTION_STRING_PREFIX = "ConnectionStrings__"
        private const val POSTGRES = "postgres"
        private const val MYSQL = "mysql"
        private const val MSSQL = "mssql"
        private const val ORACLE = "oracle"
        private const val MONGO = "mongo"
    }

    override fun resourceCreated(resource: AspireResourceService) {
        applyChanges(resource)
    }

    override fun resourceUpdated(resource: AspireResourceService) {
        applyChanges(resource)
    }

    private fun applyChanges(resource: AspireResourceService) {
        val service = DatabaseService.getInstance(project)
        if (resource.resourceType == ResourceType.Project) {
            resource.environment
                .filter { it.key.startsWith(CONNECTION_STRINGS) && it.value != null }
                .forEach {
                    if (!it.value.isNullOrEmpty()) {
                        val connectionName = it.key.substringAfter(CONNECTION_STRING_PREFIX)

                        val connectionString = DatabaseResourceConnectionString(
                            connectionName,
                            it.value,
                            resource.lifetime
                        )
                        service.addConnectionString(connectionString)
                    }
                }
        } else if (resource.resourceType == ResourceType.Container) {
            if (resource.services.isEmpty()) return
            val resourceType = getType(resource.containerImage) ?: return
            val ports = resource.services.asSequence()
                .filter { it.allocatedPort != null }
                .map { it.allocatedPort?.toString() ?: "" }
                .toList()

            val databaseResource = DatabaseResource(
                resource.name,
                resourceType,
                ports,
                resource.lifetime
            )
            service.addDatabaseResource(databaseResource)
        }
    }

    private fun getType(image: String?): DatabaseResourceType? {
        if (image == null) return null
        if (image.contains(POSTGRES)) return DatabaseResourceType.POSTGRES
        if (image.contains(MYSQL)) return DatabaseResourceType.MYSQL
        if (image.contains(MSSQL)) return DatabaseResourceType.MSSQL
        if (image.contains(ORACLE)) return DatabaseResourceType.ORACLE
        if (image.contains(MONGO)) return DatabaseResourceType.MONGO
        return null
    }
}