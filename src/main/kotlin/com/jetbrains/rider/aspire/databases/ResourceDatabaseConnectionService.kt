package com.jetbrains.rider.aspire.databases

import com.intellij.database.access.DatabaseCredentialsUi
import com.intellij.database.dataSource.DataSourceStorage
import com.intellij.database.dataSource.DatabaseDriver
import com.intellij.database.dataSource.LocalDataSource
import com.intellij.database.dataSource.LocalDataSourceManager
import com.intellij.database.util.DbImplUtil
import com.intellij.database.util.LoaderContext
import com.intellij.database.util.performAutoIntrospection
import com.intellij.docker.DockerServerRuntimesManager
import com.intellij.docker.runtimes.DockerApplicationRuntime
import com.intellij.docker.utils.findRuntimeById
import com.intellij.docker.utils.getOrCreateDockerLocalServer
import com.intellij.openapi.application.EDT
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.debug
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.diagnostic.trace
import com.intellij.openapi.project.Project
import com.intellij.platform.ide.progress.withBackgroundProgress
import com.intellij.util.application
import com.jetbrains.rd.util.lifetime.isNotAlive
import com.jetbrains.rider.aspire.AspireBundle
import com.jetbrains.rider.plugins.appender.database.dialog.steps.shared.services.connection.ConnectionManager
import com.jetbrains.rider.plugins.appender.database.dialog.steps.shared.services.connection.TestConnectionExecutionResult
import com.jetbrains.rider.plugins.appender.database.jdbcToConnectionString.converters.ConnectionStringToJdbcUrlConverter
import com.jetbrains.rider.plugins.appender.database.jdbcToConnectionString.dataProviders.*
import com.jetbrains.rider.plugins.appender.database.jdbcToConnectionString.factories.ConnectionStringsFactory
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.future.await
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Duration.Companion.milliseconds

/**
 * Service for managing database connections and associated Aspire resources.
 *
 * This service attempts to create a new [LocalDataSource] based on the provided [DatabaseResource].
 *
 * It tries to "fix" the original resource connection string by the container ports.
 * Aspire often uses proxy for the resource ports, and so they change after each restart.
 * For persistent resources we don't want to recreate the data sources each time,
 * so we have to "fix" the connection string.
 */
@Service(Service.Level.PROJECT)
class ResourceDatabaseConnectionService(private val project: Project, scope: CoroutineScope) {
    companion object {
        fun getInstance(project: Project): ResourceDatabaseConnectionService = project.service()

        private val LOG = logger<ResourceDatabaseConnectionService>()

        private const val REDIS_CONNECTION_STRING_PATTERN =
            "(?<host>\\w*):(?<port>\\d*)(,user=(?<user>\\w*))?(,password=(?<password>\\w*))?"
        private val REDIS_REGEX = Regex(REDIS_CONNECTION_STRING_PATTERN)
    }

    private val rawConnectionStringTypes = listOf(DatabaseType.MSSQL, DatabaseType.MONGO)

    private val connectionStrings = ConcurrentHashMap<String, Unit>()
    private val urlToConnectionStrings = ConcurrentHashMap<String, String>()

    private val connectionManager = ConnectionManager(project)

    private val databaseResourcesToProcess = MutableSharedFlow<DatabaseResource>(
        onBufferOverflow = BufferOverflow.SUSPEND,
        extraBufferCapacity = 100
    )
    private val createdDataSources = MutableSharedFlow<LocalDataSource>(
        onBufferOverflow = BufferOverflow.SUSPEND,
        extraBufferCapacity = 100
    )

    init {
        scope.launch {
            databaseResourcesToProcess.collect { process(it) }
        }
        scope.launch {
            createdDataSources.collect { connectToDataSource(it) }
        }
    }

    fun processDatabaseResource(databaseResource: DatabaseResource) {
        databaseResourcesToProcess.tryEmit(databaseResource)
    }

    private suspend fun process(databaseResource: DatabaseResource) {
        if (databaseResource.resourceLifetime.isNotAlive) return

        val modifyConnectionStringResult = modifyConnectionString(databaseResource)

        //If the connection string wasn't modified, use the resource one
        val connectionString = modifyConnectionStringResult.getOrDefault(databaseResource.connectionString)

        LOG.trace { "Processing connection string for $databaseResource" }

        if (connectionStrings.putIfAbsent(connectionString, Unit) != null) {
            LOG.trace { "Connection string $connectionString is already in use" }
            return
        }

        try {
            createDataSource(connectionString, modifyConnectionStringResult.isSuccess, databaseResource)
        } catch (ce: CancellationException) {
            connectionStrings.remove(connectionString)
            LOG.trace { "Connecting with $connectionString was cancelled" }
            throw ce
        } catch (e: Exception) {
            LOG.warn("Unable to connect to database $connectionString", e)
            connectionStrings.remove(connectionString)
        }
    }

    private suspend fun createDataSource(
        connectionString: String,
        connectionStringWasModified: Boolean,
        databaseResource: DatabaseResource
    ) {
        val dataProvider = getDataProvider(databaseResource.type)
        val driver = DbImplUtil.guessDatabaseDriver(dataProvider.dbms.first())
        if (driver == null) {
            LOG.info("Unable to guess database driver")
            connectionStrings.remove(connectionString)
            return
        }

        val url = getConnectionUrl(connectionString, databaseResource, dataProvider, driver)
        if (url == null) {
            LOG.info("Unable to convert $connectionString to url")
            connectionStrings.remove(connectionString)
            return
        }
        urlToConnectionStrings[url] = connectionString

        val dataSourceManager = LocalDataSourceManager.getInstance(project)
        if (dataSourceManager.dataSources.any { it.url == url }) {
            LOG.trace { "Data source with $url is already in use" }
            return
        }

        LOG.trace { "Creating a new data source with $url" }
        val createdDataSource = LocalDataSource.fromDriver(driver, url, true).apply {
            name = databaseResource.name
            isAutoSynchronize = true
        }
        withContext(Dispatchers.EDT) {
            dataSourceManager.addDataSource(createdDataSource)
        }

        // If we use the original resource connection string, or it is not a persistent resource,
        // the ports will be different after the aspire restart.
        // So for such resources we have to remove old data sources and recreate them after each start.
        if (!connectionStringWasModified || !databaseResource.isPersistent) {
            databaseResource.resourceLifetime.onTerminationIfAlive {
                LOG.trace { "Removing data source $url" }
                application.invokeLater {
                    dataSourceManager.removeDataSource(createdDataSource)
                }
            }
        }

        createdDataSources.tryEmit(createdDataSource)
    }

    //We have to modify the connection string to use the database container ports instead of proxy
    //See also: https://learn.microsoft.com/en-us/dotnet/aspire/fundamentals/networking-overview
    private suspend fun modifyConnectionString(databaseResource: DatabaseResource): Result<String> {
        val resourceUrl = databaseResource.urls.find { url ->
            databaseResource.connectionString.contains(url.port.toString())
        }

        if (resourceUrl == null) {
            LOG.warn("Unable to find resource url for ${databaseResource.connectionString}")
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

    private fun getDataProvider(type: DatabaseType): DotnetDataProvider =
        when (type) {
            DatabaseType.POSTGRES -> NpgsqlDataProvider.getInstance(project)
            DatabaseType.MYSQL -> MySqlClientDataProvider.getInstance(project)
            DatabaseType.MSSQL -> SqlClientDataProvider.getInstance(project)
            DatabaseType.ORACLE -> OracleClientDataProvider.getInstance(project)
            DatabaseType.MONGO -> DummyMongoDataProvider.getInstance(project)
            DatabaseType.REDIS -> DummyRedisDataProvider.getInstance(project)
        }

    private suspend fun getConnectionUrl(
        connectionString: String,
        databaseResource: DatabaseResource,
        dataProvider: DotnetDataProvider,
        driver: DatabaseDriver
    ): String? {
        return if (databaseResource.type == DatabaseType.REDIS) {
            convertRedisConnectionString(connectionString)
        } else if (rawConnectionStringTypes.contains(databaseResource.type)) {
            connectionString
        } else {
            val factory = ConnectionStringsFactory.get(dataProvider, project)
            if (factory == null) {
                LOG.warn("Unable to find connection string factory")
                return null
            }
            val parsedConnectionString =
                factory.create(connectionString, dataProvider).getOrNull()
            if (parsedConnectionString == null) {
                LOG.warn("Unable to parse connection string $connectionString")
                return null
            }
            ConnectionStringToJdbcUrlConverter.convert(parsedConnectionString, driver, project)
                ?.build()
                ?.getOrNull()
        }
    }

    private fun convertRedisConnectionString(connectionString: String): String? {
        val matchResult = REDIS_REGEX.matchEntire(connectionString) ?: return null

        val host = matchResult.groups["host"]?.value
        val port = matchResult.groups["port"]?.value
        val user = matchResult.groups["user"]?.value
        val password = matchResult.groups["password"]?.value

        val sb = StringBuilder("jdbc:redis://")
        user?.let { sb.append(it).append(":") }
        password?.let { sb.append(it).append("@") }
        host?.let { sb.append(it) }
        port?.let { sb.append(":").append(it) }

        return sb.toString()
    }

    private suspend fun connectToDataSource(dataSource: LocalDataSource) =
        withBackgroundProgress(project, AspireBundle.message("progress.connecting.to.database")) {
            val isConnectionSuccessful = waitForConnection(dataSource)
            if (!isConnectionSuccessful) {
                LOG.warn("Unable to connect to database")
                return@withBackgroundProgress
            }

            try {
                performAutoIntrospection(LoaderContext.selectGeneralTask(project, dataSource), true)
            } catch (ce: CancellationException) {
                LOG.trace("Introspection is canceled")
                throw ce
            } catch (e: Exception) {
                LOG.warn("Unable to perform auto introspection", e)
            }
        }

    private suspend fun waitForConnection(dataSource: LocalDataSource): Boolean {
        val credentials = DatabaseCredentialsUi.newUIInstance()

        (1..<5).forEach { _ ->
            when (val connectionResult = connectionManager.testConnection(dataSource, credentials)) {
                TestConnectionExecutionResult.Cancelled -> {
                    LOG.debug("Connection cancelled")
                    return false
                }

                is TestConnectionExecutionResult.Failure -> {
                    LOG.debug { "Unable to connect to database, ${connectionResult.result.summary}" }
                    delay(300.milliseconds)
                }

                is TestConnectionExecutionResult.Success -> return true
            }
        }

        return false
    }

    private fun removeConnectionStringByUrl(url: String?) {
        if (url == null) return
        val connectionString = urlToConnectionStrings.remove(url) ?: return
        connectionStrings.remove(connectionString)
    }

    class DataSourceListener(private val project: Project) : DataSourceStorage.Listener {
        override fun dataSourceRemoved(dataSource: LocalDataSource) {
            getInstance(project).removeConnectionStringByUrl(dataSource.url)
        }
    }
}