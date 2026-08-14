package com.jetbrains.aspire.rider.debugger

import com.intellij.openapi.application.EDT
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.diagnostic.trace
import com.intellij.openapi.project.Project
import com.jetbrains.aspire.extensions.AppHostEntryPoint
import com.jetbrains.aspire.extensions.AppHostEntryPointLocator
import com.jetbrains.aspire.rider.AspirePluginModelHost
import com.jetbrains.rider.ijent.extensions.toNioPathOrNull
import com.jetbrains.rider.ijent.extensions.toRd
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.nio.file.Path
import kotlin.io.path.extension
import kotlin.time.Duration.Companion.minutes

/**
 * Resolves the `DistributedApplication.CreateBuilder(...)` call of a .NET AppHost on the ReSharper backend.
 *
 * The line comes from backend PSI rather than a text search so that matches inside comments and strings are
 * excluded, and so that a renamed entry file still resolves. Backend PSI lags frontend typing, but the caller
 * re-resolves on every relevant event, so a stale answer corrects itself.
 */
internal class DotNetAppHostEntryPointLocator : AppHostEntryPointLocator {
    companion object {
        private val LOG = logger<DotNetAppHostEntryPointLocator>()

        // Solution load can take a while on a cold start; give up rather than wait forever in a project that
        // never loads a .NET solution at all.
        private val MODEL_TIMEOUT = 2.minutes
    }

    override val priority: Int = 100

    override fun isApplicable(appHostFilePath: Path): Boolean =
        when (appHostFilePath.extension.lowercase()) {
            "csproj", "cs" -> true // C# project or single-file (apphost.cs) AppHost
            else -> false
        }

    override suspend fun findEntryPoint(appHostFilePath: Path, project: Project): AppHostEntryPoint? {
        val model = AspirePluginModelHost.getInstance(project).awaitModel(MODEL_TIMEOUT)
        if (model == null) {
            LOG.warn("The Aspire backend model did not appear within $MODEL_TIMEOUT; cannot resolve $appHostFilePath")
            return null
        }

        val response = try {
            withContext(Dispatchers.EDT) {
                model.getAppHostEntryPoint.startSuspending(appHostFilePath.toRd())
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            LOG.warn("Unable to resolve the AppHost entry point of $appHostFilePath", e)
            return null
        }

        if (response == null) {
            LOG.trace { "The backend found no DistributedApplication.CreateBuilder call for $appHostFilePath" }
            return null
        }

        val entryFilePath = response.filePath.toNioPathOrNull()
        if (entryFilePath == null) {
            LOG.warn("The backend reported an unusable AppHost entry file path: ${response.filePath}")
            return null
        }

        return AppHostEntryPoint(entryFilePath, response.createBuilderLine)
    }
}
