package com.jetbrains.aspire.database

import com.intellij.database.util.DbImplUtil
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.diagnostic.trace
import com.intellij.openapi.project.Project
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlin.coroutines.cancellation.CancellationException

/**
 * Service for managing database connections and associated Aspire resources.
 *
 * This service attempts to create a new [com.intellij.database.dataSource.LocalDataSource]
 * based on the provided [DatabaseResource].
 *
 * It tries to "fix" the original resource connection string by the container ports.
 * Aspire often uses proxy for the resource ports, and so they change after each restart.
 * For persistent resources we don't want to recreate the data sources each time,
 * so we have to "fix" the connection string.
 */
@Service(Service.Level.PROJECT)
internal class DatabaseResourceConnectionService(private val project: Project, scope: CoroutineScope) {
    companion object {
        fun getInstance(project: Project): DatabaseResourceConnectionService = project.service()

        private val LOG = logger<DatabaseResourceConnectionService>()
    }

    private val connectionCommands = Channel<DatabaseResourceConnectionCommand>(Channel.UNLIMITED)

    init {
        scope.launch {
            for (command in connectionCommands) {
                when (command) {
                    is AddDatabaseResourceConnection -> addConnection(command.resource)
                    is RemoveDatabaseResourceConnection -> removeConnection(command.resourceId)
                }
            }
        }
    }

    fun sendConnectionCommand(command: DatabaseResourceConnectionCommand) {
        connectionCommands.trySend(command)
    }

    private suspend fun addConnection(databaseResource: DatabaseResource) {
        //This `addConnection` method can be called multiple times for a single resource (on each update),
        //and we don't want to recreate the connection
        val dataSourceManager = DatabaseDataSourceManager.getInstance(project)
        if (!dataSourceManager.tryRegisterResourceId(databaseResource.resourceId)) {
            LOG.trace { "Connection for resource ${databaseResource.name} was already created" }
            return
        }

        try {
            val connectionStringModifier = DatabaseConnectionStringModifier.getInstance(project)
            val modifyConnectionStringResult = connectionStringModifier.modifyConnectionString(databaseResource)

            //If the connection string wasn't modified, use the resource one
            val connectionString = modifyConnectionStringResult.getOrDefault(databaseResource.connectionString)

            LOG.trace { "Processing connection string for ${databaseResource.name}" }

            val urlConverter = DatabaseConnectionUrlConverter.getInstance(project)
            val dataProvider = urlConverter.getDataProvider(databaseResource.type)
            val driver = DbImplUtil.guessDatabaseDriver(dataProvider.dbms.first())
            if (driver == null) {
                LOG.info("Unable to guess database driver for ${databaseResource.name}")
                dataSourceManager.unregisterResourceId(databaseResource.resourceId)
                return
            }

            val url = urlConverter.getConnectionUrl(connectionString, databaseResource, dataProvider, driver)
            if (url == null) {
                LOG.info("Unable to convert a connection string to an url for ${databaseResource.name}")
                dataSourceManager.unregisterResourceId(databaseResource.resourceId)
                return
            }

            val createdDataSource = dataSourceManager.createDataSource(databaseResource, driver, url)
            if (createdDataSource == null) {
                dataSourceManager.unregisterResourceId(databaseResource.resourceId)
                return
            }

            val connectionTester = DatabaseConnectionTester.getInstance(project)
            connectionTester.connectToDataSource(createdDataSource)
        } catch (ce: CancellationException) {
            dataSourceManager.unregisterResourceId(databaseResource.resourceId)
            LOG.trace { "Connecting with to database resource ${databaseResource.name} was cancelled" }
            throw ce
        } catch (e: Exception) {
            LOG.warn("Unable to connect to database ${databaseResource.name}", e)
            dataSourceManager.unregisterResourceId(databaseResource.resourceId)
        }
    }

    private suspend fun removeConnection(resourceId: String) {
        val dataSourceManager = DatabaseDataSourceManager.getInstance(project)
        dataSourceManager.unregisterResourceId(resourceId)
        dataSourceManager.removeDataSource(resourceId)
    }

    sealed interface DatabaseResourceConnectionCommand
    data class AddDatabaseResourceConnection(val resource: DatabaseResource) : DatabaseResourceConnectionCommand
    data class RemoveDatabaseResourceConnection(val resourceId: String) : DatabaseResourceConnectionCommand
}
