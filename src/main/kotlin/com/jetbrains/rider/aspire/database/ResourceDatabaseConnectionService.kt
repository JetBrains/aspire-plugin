package com.jetbrains.rider.aspire.database

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
 * This service attempts
 * to create a new [LocalDataSource] based on the provided [DatabaseResource] and [SessionConnectionString].
 *
 * 1. The service tries to use the internal ports of [DatabaseResource],
 * as they remain the same for persistent resources.
 * 2. If a [LocalDataSource] has been created, the service attempts to connect to it.
 * 3. If the [DatabaseResource] is not persistent,
 * the created [LocalDataSource] will be deleted when the lifetime of the resource terminates.
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

    private val connectionToProcess = MutableSharedFlow<Pair<SessionConnectionString, DatabaseResource>>(
        onBufferOverflow = BufferOverflow.SUSPEND,
        extraBufferCapacity = 100
    )
    private val createdDataSources = MutableSharedFlow<LocalDataSource>(
        onBufferOverflow = BufferOverflow.SUSPEND,
        extraBufferCapacity = 100
    )

    init {
        scope.launch {
            connectionToProcess.collect { process(it.first, it.second) }
        }
        scope.launch {
            createdDataSources.collect { connectToDataSource(it) }
        }
    }

    suspend fun processConnection(connectionString: SessionConnectionString, databaseResource: DatabaseResource) {
        connectionToProcess.emit(connectionString to databaseResource)
    }

    private suspend fun process(connectionString: SessionConnectionString, databaseResource: DatabaseResource) {
        if (databaseResource.resourceLifetime.isNotAlive) return

        val modifiedConnectionString = modifyConnectionString(connectionString, databaseResource)

        LOG.trace { "Processing connection string $connectionString for $databaseResource" }

        if (connectionStrings.putIfAbsent(modifiedConnectionString, Unit) != null) {
            LOG.trace { "Connection string $modifiedConnectionString is already in use" }
            return
        }

        try {
            createDataSource(databaseResource, modifiedConnectionString, connectionString)
        } catch (ce: CancellationException) {
            connectionStrings.remove(modifiedConnectionString)
            LOG.trace { "Connecting with $modifiedConnectionString was cancelled" }
            throw ce
        } catch (e: Exception) {
            LOG.warn("Unable to connect to database $modifiedConnectionString", e)
            connectionStrings.remove(modifiedConnectionString)
        }
    }

    private suspend fun createDataSource(
        databaseResource: DatabaseResource,
        modifiedConnectionString: String,
        connectionString: SessionConnectionString
    ) {
        val dataProvider = getDataProvider(databaseResource.type)
        val driver = DbImplUtil.guessDatabaseDriver(dataProvider.dbms.first())
        if (driver == null) {
            LOG.info("Unable to guess database driver")
            connectionStrings.remove(modifiedConnectionString)
            return
        }

        val url = getConnectionUrl(modifiedConnectionString, databaseResource, dataProvider, driver)
        if (url == null) {
            LOG.info("Unable to convert $modifiedConnectionString to url")
            connectionStrings.remove(modifiedConnectionString)
            return
        }
        urlToConnectionStrings[url] = modifiedConnectionString

        val dataSourceManager = LocalDataSourceManager.getInstance(project)
        val createdDataSource = if (dataSourceManager.dataSources.any { it.url == url }) {
            LOG.trace { "Data source with $url is already in use" }
            null
        } else {
            LOG.trace { "Creating a new data source with $url" }
            val dataSource = LocalDataSource.fromDriver(driver, url, true).apply {
                name = connectionString.connectionName
                isAutoSynchronize = true
            }
            withContext(Dispatchers.EDT) {
                dataSourceManager.addDataSource(dataSource)
            }
            dataSource
        }

        if (!databaseResource.isPersistent && createdDataSource != null) {
            databaseResource.resourceLifetime.onTerminationIfAlive {
                LOG.trace { "Removing data source $url" }
                application.invokeLater {
                    dataSourceManager.removeDataSource(createdDataSource)
                }
            }
        }

        createdDataSource?.let { createdDataSources.tryEmit(it) }
    }

    //We have to modify the connection string to use the database container ports instead of proxy
    //See also: https://learn.microsoft.com/en-us/dotnet/aspire/fundamentals/networking-overview
    private suspend fun modifyConnectionString(
        connectionString: SessionConnectionString,
        databaseResource: DatabaseResource
    ): String {
        val resourceUrl = databaseResource.urls.find {
            connectionString.connectionString.contains(it.uri.port.toString())
        }

        if (resourceUrl == null) {
            LOG.warn("Unable to find resource url for ${connectionString.connectionString}")
            return connectionString.connectionString
        }

        val containerPorts = getContainerPorts(databaseResource.containerId)
        if (containerPorts.isEmpty()) {
            LOG.info("Unable to get container ports for ${databaseResource.containerId}")
            return connectionString.connectionString
        }
        LOG.trace { "Found container ports for ${containerPorts.joinToString()}" }

        val resourcePort = getResourcePort(databaseResource)
        if (resourcePort == null) {
            LOG.info("Unable to find resource port for ${databaseResource.containerId}")
            return connectionString.connectionString
        }
        LOG.trace { "Found resource port $resourcePort" }

        val targetPort = containerPorts.firstOrNull { it.second == resourcePort }?.first?.toString()
        if (targetPort == null) {
            LOG.warn("Unable to find a corresponding resource port $resourcePort from container ports")
            return connectionString.connectionString
        }

        val sourcePort = resourceUrl.uri.port.toString()

        return connectionString.connectionString.replace(sourcePort, targetPort)
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