package com.jetbrains.aspire.database

import com.intellij.docker.DockerServerRuntimesManager
import com.intellij.docker.runtimes.DockerApplicationRuntime
import com.intellij.docker.utils.findRuntimeById
import com.intellij.docker.utils.getOrCreateDockerLocalServer
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.diagnostic.trace
import com.intellij.openapi.project.Project
import kotlinx.coroutines.future.await
import kotlin.coroutines.cancellation.CancellationException

/**
 * Modifies database resource connection strings by replacing Aspire proxy ports
 * with actual Docker container ports.
 *
 * Aspire uses proxy ports that change after each restart, so this service resolves
 * the real container ports via the Docker API to produce stable connection strings
 * for persistent resources.
 *
 * @see <a href="https://learn.microsoft.com/en-us/dotnet/aspire/fundamentals/networking-overview">Aspire Networking Overview</a>
 */
@Service(Service.Level.PROJECT)
internal class DatabaseConnectionStringModifier(private val project: Project) {
    companion object {
        fun getInstance(project: Project): DatabaseConnectionStringModifier = project.service()

        private val LOG = logger<DatabaseConnectionStringModifier>()
    }

    suspend fun modifyConnectionString(databaseResource: DatabaseResource): Result<String> {
        val resourceUrl = databaseResource.urls.find { url ->
            databaseResource.connectionString.contains(url.port.toString())
        }

        if (resourceUrl == null) {
            LOG.warn("Unable to find resource url for ${databaseResource.name}")
            return Result.failure(IllegalStateException())
        }

        val containerPorts = getContainerPorts(databaseResource.containerId)
        if (containerPorts.isEmpty()) {
            LOG.info("Unable to get container ports for ${databaseResource.containerId}")
            return Result.failure(IllegalStateException())
        }
        LOG.trace { "Found container ports for ${containerPorts.joinToString()}" }

        val resourcePort = getResourcePort(databaseResource)
        if (resourcePort == null) {
            LOG.info("Unable to find resource port for ${databaseResource.containerId}")
            return Result.failure(IllegalStateException())
        }
        LOG.trace { "Found resource port $resourcePort" }

        val targetPort = containerPorts.firstOrNull { it.second == resourcePort }?.first?.toString()
        if (targetPort == null) {
            LOG.warn("Unable to find a corresponding resource port $resourcePort from container ports")
            return Result.failure(IllegalStateException())
        }

        val sourcePort = resourceUrl.port.toString()

        return Result.success(databaseResource.connectionString.replace(sourcePort, targetPort))
    }

    private suspend fun getContainerPorts(containerId: String): List<Pair<Int?, Int?>> {
        try {
            val localServer = getOrCreateDockerLocalServer()
            val manager = DockerServerRuntimesManager.getInstance(project)
            val serverRuntime = manager.getOrCreateConnection(localServer).await()
            val container = serverRuntime.runtimesManager.findRuntimeById<DockerApplicationRuntime>(containerId)
            if (container == null) {
                LOG.trace { "Unable to find container runtime with id $containerId" }
                return emptyList()
            }

            return container.agentContainer.container.ports.map { it.publicPort to it.privatePort }
        } catch (ce: CancellationException) {
            throw ce
        } catch (e: Exception) {
            LOG.warn("Unable to get container port for $containerId", e)
            return emptyList()
        }
    }

    private fun getResourcePort(databaseResource: DatabaseResource): Int? {
        val portString = databaseResource.containerPorts ?: return null

        return portString
            .removePrefix("[")
            .removeSuffix("]")
            .split(",")
            .firstNotNullOfOrNull { it.trim().toIntOrNull() }
    }
}
