package com.jetbrains.aspire.database

import com.intellij.database.access.DatabaseCredentialsUi
import com.intellij.database.dataSource.LocalDataSource
import com.intellij.database.util.LoaderContext
import com.intellij.database.util.performAutoIntrospection
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.debug
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.platform.ide.progress.withBackgroundProgress
import com.jetbrains.rider.plugins.appender.database.dialog.steps.shared.services.connection.ConnectionManager
import com.jetbrains.rider.plugins.appender.database.dialog.steps.shared.services.connection.TestConnectionExecutionResult
import kotlinx.coroutines.delay
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Duration.Companion.milliseconds

/**
 * Tests database connections with retry logic and performs automatic schema introspection.
 *
 * Attempts to connect up to 4 times with a 300ms delay between retries.
 * On successful connection, triggers auto-introspection to populate the database schema
 * in IntelliJ's Database tool window.
 */
@Service(Service.Level.PROJECT)
internal class DatabaseConnectionTester(private val project: Project) {
    companion object {
        fun getInstance(project: Project): DatabaseConnectionTester = project.service()

        private val LOG = logger<DatabaseConnectionTester>()
    }

    suspend fun connectToDataSource(dataSource: LocalDataSource) =
        withBackgroundProgress(project, AspireDatabaseBundle.message("progress.connecting.to.database")) {
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

        val connectionManager = ConnectionManager(project)
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
}
