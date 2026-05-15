package com.jetbrains.aspire.database

import com.intellij.database.access.DatabaseCredentialsUi
import com.intellij.database.dataSource.LocalDataSource
import com.intellij.database.dataSource.ui.DataSourceTestConnectionManager
import com.intellij.database.util.AsyncUtil
import com.intellij.database.util.ErrorHandler
import com.intellij.database.util.LoaderContext
import com.intellij.database.util.performAutoIntrospection
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.debug
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.platform.ide.progress.withBackgroundProgress
import com.intellij.util.ThreeState
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

        (1..<5).forEach { _ ->
            val errorHandler = ErrorHandler()
            val rawResult = runCatching {
                DataSourceTestConnectionManager.performTestConnection(
                    project,
                    dataSource,
                    credentials,
                    errorHandler,
                    false
                )
            }
            val error = rawResult.exceptionOrNull()

            if (AsyncUtil.isCancellation(error) && !errorHandler.hasErrors()) {
                LOG.debug("Connection cancelled")
                return false
            }

            if (error != null) {
                AsyncUtil.addUnhandledError(errorHandler, error, dataSource, project)
            }

            val processed = DataSourceTestConnectionManager.processTestConnectionResult(
                rawResult.getOrNull(),
                dataSource,
                errorHandler
            )
            when (processed.state) {
                ThreeState.YES -> {
                    LOG.debug { "Unable to connect to database, ${processed.summary}" }
                    delay(300.milliseconds)
                }

                ThreeState.UNSURE,
                ThreeState.NO -> return true
            }
        }

        return false
    }
}
