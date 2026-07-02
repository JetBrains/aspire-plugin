package com.jetbrains.aspire.docker

import com.intellij.docker.DockerServerRuntimesManager
import com.intellij.docker.runtimes.DockerApplicationRuntime
import com.intellij.docker.utils.findRuntimeById
import com.intellij.docker.utils.getOrCreateDockerLocalServer
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.diagnostic.trace
import com.intellij.openapi.project.Project
import com.jetbrains.aspire.extensions.ConnectionStringContext
import com.jetbrains.aspire.extensions.ConnectionStringModifier
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
internal class DockerConnectionStringModifier : ConnectionStringModifier {
    companion object {
        private val LOG = logger<DockerConnectionStringModifier>()
    }

    override suspend fun modifyConnectionString(project: Project, context: ConnectionStringContext): Result<String> {
        try {
            val resourceUrl = context.urls.find { url ->
                context.connectionString.contains(url.port.toString())
            }

            if (resourceUrl == null) {
                LOG.warn("Unable to find resource url for ${context.name}")
                return Result.failure(IllegalStateException())
            }

            val containerPorts = getContainerPorts(project, context.containerId)
            if (containerPorts.isEmpty()) {
                LOG.info("Unable to get container ports for ${context.containerId}")
                return Result.failure(IllegalStateException())
            }
            LOG.trace { "Found container ports for ${containerPorts.joinToString()}" }

            val resourcePort = getResourcePort(context.containerPorts)
            if (resourcePort == null) {
                LOG.info("Unable to find resource port for ${context.containerId}")
                return Result.failure(IllegalStateException())
            }
            LOG.trace { "Found resource port $resourcePort" }

            val targetPort = containerPorts.firstOrNull { it.second == resourcePort }?.first?.toString()
            if (targetPort == null) {
                LOG.warn("Unable to find a corresponding resource port $resourcePort from container ports")
                return Result.failure(IllegalStateException())
            }

            val sourcePort = resourceUrl.port.toString()

            return Result.success(context.connectionString.replace(sourcePort, targetPort))
        } catch (ce: CancellationException) {
            throw ce
        } catch (e: Exception) {
            LOG.error("Failed to modify connection string for ${context.name}", e)
            return Result.failure(e)
        }
    }

    private suspend fun getContainerPorts(project: Project, containerId: String): List<Pair<Int?, Int?>> {
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

    private fun getResourcePort(containerPorts: String?): Int? {
        val portString = containerPorts ?: return null

        return portString
            .removePrefix("[")
            .removeSuffix("]")
            .split(",")
            .firstNotNullOfOrNull { it.trim().toIntOrNull() }
    }
}
