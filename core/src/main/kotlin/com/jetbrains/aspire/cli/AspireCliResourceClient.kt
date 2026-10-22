@file:Suppress("UnstableApiUsage")

package com.jetbrains.aspire.cli

import com.intellij.execution.ExecutionException
import com.intellij.openapi.project.Project
import com.intellij.platform.eel.provider.asEelPath
import com.intellij.platform.eel.provider.getEelDescriptor
import com.intellij.platform.eel.provider.toEelApi
import com.intellij.platform.eel.spawnProcess
import com.jetbrains.aspire.AspireCoreBundle
import com.jetbrains.aspire.resources.AspireResourceClient
import com.jetbrains.aspire.resources.AspireResourceCommandRequest
import com.jetbrains.aspire.resources.AspireResourceCommandResponse
import com.jetbrains.aspire.resources.AspireResourceUpdate
import com.jetbrains.aspire.worker.AspireAppHostPath
import com.jetbrains.aspire.worker.AspireResourceLogEntry
import com.jetbrains.aspire.worker.toNioPath
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
interface AspireCliResourceClientFactory {
    fun create(project: Project): AspireResourceClient
}

internal class AspireCliResourceClientFactoryImpl : AspireCliResourceClientFactory {
    override fun create(project: Project): AspireResourceClient {
        return AspireCliResourceClient(project)
    }
}

internal class AspireCliResourceClient(private val project: Project) : AspireResourceClient {
    /**
     * Starts a separate CLI process for each collector and stops it when collection is cancelled.
     * Emits each resource as an upsert, including the initial resources, not full snapshots.
     */
    override fun watchResources(appHostPath: AspireAppHostPath): Flow<AspireResourceUpdate> {
        return AspireCliResourceWatcher {
            val cliPath = AspireCliLocator.getInstance(project).locate()
                ?: throw ExecutionException(AspireCoreBundle.message("run.configuration.cli.error.cli.not.found"))
            val eelApi = project.getEelDescriptor().toEelApi()
            val appHost = appHostPath.toNioPath().asEelPath()

            eelApi.exec.spawnProcess(cliPath)
                .args("describe", "--include-hidden", "--follow", "--format", "json", "--nologo", "--non-interactive", "--apphost", appHost.toString())
                .workingDirectory(appHost.parent)
                .scope(this)
                .eelIt()
        }.watchResources().map { it.toAspireResourceUpdate(appHostPath) }
    }

    override fun watchResourceConsoleLogs(appHostPath: AspireAppHostPath, resourceName: String): Flow<List<AspireResourceLogEntry>> {
        return AspireCliResourceWatcher {
            val cliPath = AspireCliLocator.getInstance(project).locate()
                ?: throw ExecutionException(AspireCoreBundle.message("run.configuration.cli.error.cli.not.found"))
            val eelApi = project.getEelDescriptor().toEelApi()
            val appHost = appHostPath.toNioPath().asEelPath()

            eelApi.exec.spawnProcess(cliPath)
                .args("logs", resourceName, "--follow", "--format", "json", "--nologo", "--non-interactive", "--apphost", appHost.toString())
                .workingDirectory(appHost.parent)
                .scope(this)
                .eelIt()
        }.watchResourceConsoleLogs()
    }

    override suspend fun executeResourceCommand(appHostPath: AspireAppHostPath, request: AspireResourceCommandRequest): AspireResourceCommandResponse {
        val cliPath = AspireCliLocator.getInstance(project).locate()
            ?: throw ExecutionException(AspireCoreBundle.message("run.configuration.cli.error.cli.not.found"))
        val eelApi = project.getEelDescriptor().toEelApi()
        val appHost = appHostPath.toNioPath().asEelPath()

        return AspireCliResourceCommandRunner { args ->
            eelApi.exec.spawnProcess(cliPath)
                .args(args)
                .env(mapOf("ASPIRE_NON_INTERACTIVE" to "true"))
                .workingDirectory(appHost.parent)
                .scope(this)
                .eelIt()
        }.executeResourceCommand(appHost.toString(), request)
    }

    override fun shutdown() = Unit
}