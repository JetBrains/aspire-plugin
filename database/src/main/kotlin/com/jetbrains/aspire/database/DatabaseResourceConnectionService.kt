package com.jetbrains.aspire.database

import com.intellij.database.util.DbImplUtil
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.diagnostic.trace
import com.intellij.openapi.project.Project
import com.jetbrains.rd.util.lifetime.isNotAlive
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
                }
            }
        }
    }

    fun sendConnectionCommand(command: DatabaseResourceConnectionCommand) {
        connectionCommands.trySend(command)
    }

    private suspend fun addConnection(databaseResource: DatabaseResource) {
        if (databaseResource.resourceLifetime.isNotAlive) return

        val connectionStringModifier = DatabaseConnectionStringModifier.getInstance(project)
        val modifyConnectionStringResult = connectionStringModifier.modifyConnectionString(databaseResource)

        //If the connection string wasn't modified, use the resource one
        val connectionString = modifyConnectionStringResult.getOrDefault(databaseResource.connectionString)

        LOG.trace { "Processing connection string for ${databaseResource.name}" }

        val dataSourceManager = DatabaseDataSourceManager.getInstance(project)
        if (!dataSourceManager.tryRegisterConnectionString(connectionString)) {
            LOG.trace { "Connection string for ${databaseResource.name} is already in use" }
            return
        }

        try {
            val urlConverter = DatabaseConnectionUrlConverter.getInstance(project)
            val dataProvider = urlConverter.getDataProvider(databaseResource.type)
            val driver = DbImplUtil.guessDatabaseDriver(dataProvider.dbms.first())
            if (driver == null) {
                LOG.info("Unable to guess database driver for ${databaseResource.name}")
                dataSourceManager.unregisterConnectionString(connectionString)
                return
            }

            val url = urlConverter.getConnectionUrl(connectionString, databaseResource, dataProvider, driver)
            if (url == null) {
                LOG.info("Unable to convert a connection string to an url for ${databaseResource.name}")
                dataSourceManager.unregisterConnectionString(connectionString)
                return
            }

            val createdDataSource = dataSourceManager.createDataSource(
                connectionString,
                modifyConnectionStringResult.isSuccess,
                databaseResource,
                driver,
                url
            ) ?: return

            val connectionTester = DatabaseConnectionTester.getInstance(project)
            connectionTester.connectToDataSource(createdDataSource)
        } catch (ce: CancellationException) {
            dataSourceManager.unregisterConnectionString(connectionString)
            LOG.trace { "Connecting with to database resource ${databaseResource.name} was cancelled" }
            throw ce
        } catch (e: Exception) {
            LOG.warn("Unable to connect to database ${databaseResource.name}", e)
            dataSourceManager.unregisterConnectionString(connectionString)
        }
    }

    sealed interface DatabaseResourceConnectionCommand
    data class AddDatabaseResourceConnection(val resource: DatabaseResource) : DatabaseResourceConnectionCommand
}
