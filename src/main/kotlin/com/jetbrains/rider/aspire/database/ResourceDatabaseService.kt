@file:Suppress("DuplicatedCode")

package com.jetbrains.rider.aspire.database

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.diagnostic.trace
import com.intellij.openapi.project.Project
import com.jetbrains.rd.util.lifetime.isNotAlive
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap

@Service(Service.Level.PROJECT)
class ResourceDatabaseService(private val project: Project, scope: CoroutineScope) {
    companion object {
        fun getInstance(project: Project): ResourceDatabaseService = project.service()

        private val LOG = logger<ResourceDatabaseService>()
    }

    private val connectionStrings = ConcurrentHashMap<Pair<String, String>, SessionConnectionString>()
    private val databaseResources = ConcurrentHashMap<String, DatabaseResource2>()

    private val connectionStringToProcess = MutableSharedFlow<SessionConnectionString>(
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
        extraBufferCapacity = 100
    )
    private val databaseResourceToProcess = MutableSharedFlow<DatabaseResource2>(
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
        extraBufferCapacity = 100
    )

    init {
        scope.launch {
            connectionStringToProcess.collect { process(it) }
        }
        scope.launch {
            databaseResourceToProcess.collect { process(it) }
        }
    }

    fun putConnectionString(connectionString: SessionConnectionString) {
        if (connectionString.sessionLifetime.isNotAlive) return

        LOG.trace { "Adding connection string $connectionString" }

        val key = connectionString.sessionId to connectionString.connectionName
        val previousValue = connectionStrings.putIfAbsent(key, connectionString)

        if (previousValue == null) {
            connectionString.sessionLifetime.onTerminationIfAlive {
                connectionStrings.remove(key)
            }

            connectionStringToProcess.tryEmit(connectionString)
        }
    }

    fun putDatabaseResource(resource: DatabaseResource2) {
        if (resource.resourceLifetime.isNotAlive) return

        LOG.trace { "Adding database resource $resource" }

        databaseResources.put(resource.containerId, resource)
        resource.resourceLifetime.onTerminationIfAlive {
            databaseResources.remove(resource.containerId)
        }

        databaseResourceToProcess.tryEmit(resource)
    }

    private fun process(connectionString: SessionConnectionString) {
        val databases = mutableListOf<DatabaseResource2>()
        for (databaseResource in databaseResources.values) {
            for (url in databaseResource.urls) {
                if (connectionString.connectionString.contains(url.uri.port.toString())) {
                    databases.add(databaseResource)
                    break
                }
            }
        }
        val service = ResourceDatabaseConnectionService.getInstance(project)
        databases.forEach {
            service.processConnection(connectionString, it)
        }
    }

    private fun process(databaseResource: DatabaseResource2) {
        val connections = mutableListOf<SessionConnectionString>()
        for (connectionString in connectionStrings.values) {
            for (url in databaseResource.urls) {
                if (connectionString.connectionString.contains(url.uri.port.toString())) {
                    connections.add(connectionString)
                    break
                }
            }
        }
        val service = ResourceDatabaseConnectionService.getInstance(project)
        connections.forEach {
            service.processConnection(it, databaseResource)
        }
    }
}

