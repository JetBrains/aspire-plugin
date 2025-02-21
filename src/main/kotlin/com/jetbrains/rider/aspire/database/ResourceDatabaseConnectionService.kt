package com.jetbrains.rider.aspire.database

import com.intellij.database.access.DatabaseCredentialsUi
import com.intellij.database.dataSource.DatabaseDriver
import com.intellij.database.dataSource.LocalDataSource
import com.intellij.database.dataSource.LocalDataSourceManager
import com.intellij.database.util.DbImplUtil
import com.intellij.database.util.LoaderContext
import com.intellij.database.util.performAutoIntrospection
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.debug
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.diagnostic.trace
import com.intellij.openapi.project.Project
import com.intellij.platform.ide.progress.withBackgroundProgress
import com.jetbrains.rd.util.lifetime.isNotAlive
import com.jetbrains.rider.aspire.AspireBundle
import com.jetbrains.rider.plugins.appender.database.dialog.steps.shared.services.connection.ConnectionManager
import com.jetbrains.rider.plugins.appender.database.dialog.steps.shared.services.connection.TestConnectionExecutionResult
import com.jetbrains.rider.plugins.appender.database.jdbcToConnectionString.converters.ConnectionStringToJdbcUrlConverter
import com.jetbrains.rider.plugins.appender.database.jdbcToConnectionString.dataProviders.*
import com.jetbrains.rider.plugins.appender.database.jdbcToConnectionString.factories.ConnectionStringsFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Duration.Companion.milliseconds

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

    private val connectionManager = ConnectionManager(project)

    private val connectionToProcess = MutableSharedFlow<Pair<SessionConnectionString, DatabaseResource>>(
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
        extraBufferCapacity = 100
    )
    private val createdDataSources = MutableSharedFlow<LocalDataSource>(
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
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

    fun processConnection(connectionString: SessionConnectionString, databaseResource: DatabaseResource) {
        connectionToProcess.tryEmit(connectionString to databaseResource)
    }

    private suspend fun process(connectionString: SessionConnectionString, databaseResource: DatabaseResource) {
        if (databaseResource.resourceLifetime.isNotAlive) return

        val modifiedConnectionString = modifyConnectionString(connectionString, databaseResource) ?: return

        LOG.trace { "Processing connection string $connectionString for $databaseResource" }

        if (connectionStrings.putIfAbsent(modifiedConnectionString, Unit) != null) {
            LOG.trace { "Connection string $modifiedConnectionString is already in use" }
            return
        }

        try {
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
                dataSourceManager.addDataSource(dataSource)
                dataSource
            }

            if (!databaseResource.isPersistent && createdDataSource != null) {
                databaseResource.resourceLifetime.onTerminationIfAlive {
                    LOG.trace { "Removing data source $url" }
                    dataSourceManager.removeDataSource(createdDataSource)
                }
            }

            createdDataSource?.let { createdDataSources.tryEmit(it) }
        } catch (ce: CancellationException) {
            connectionStrings.remove(modifiedConnectionString)
            LOG.trace { "Connecting with $modifiedConnectionString was cancelled" }
            throw ce
        } catch (e: Exception) {
            LOG.warn("Unable to connect to database $modifiedConnectionString", e)
            connectionStrings.remove(modifiedConnectionString)
        }
    }

    //We have to modify the connection string to use the iternal url because it doesn't change for the persistent resources
    private fun modifyConnectionString(
        connectionString: SessionConnectionString,
        databaseResource: DatabaseResource
    ): String? {
        val targetUrl = databaseResource.urls.find {
            connectionString.connectionString.contains(it.uri.port.toString())
        }

        if (targetUrl == null) {
            LOG.warn("Unable to find target url for ${connectionString.connectionString}")
            return null
        }

        if (targetUrl.isInternal) return connectionString.connectionString

        val targetInternalUrl = databaseResource.urls.find {
            it.isInternal && it.name.startsWith(targetUrl.name)
        }

        if (targetInternalUrl == null) {
            LOG.warn("Unable to find target internal url for ${connectionString.connectionString}")
            return connectionString.connectionString
        }

        return connectionString.connectionString.replace(
            targetUrl.uri.port.toString(),
            targetInternalUrl.uri.port.toString()
        )
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

        (1..<5).forEach { i ->
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
}