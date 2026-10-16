@file:Suppress("UnstableApiUsage")

package com.jetbrains.aspire.cli

import com.intellij.execution.ExecutionException
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.platform.eel.provider.asEelPath
import com.intellij.platform.eel.provider.getEelDescriptor
import com.intellij.platform.eel.provider.toEelApi
import com.intellij.platform.eel.spawnProcess
import com.jetbrains.aspire.AspireCoreBundle
import com.jetbrains.aspire.resources.AspireResourceUpdate
import com.jetbrains.aspire.resources.AspireResourceWatcher
import com.jetbrains.aspire.worker.AspireAppHostPath
import com.jetbrains.aspire.worker.toNioPath
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
@Service(Service.Level.PROJECT)
class AspireCliResourceClient(private val project: Project) : AspireResourceWatcher {
    companion object {
        fun getInstance(project: Project): AspireCliResourceClient = project.service()
    }

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
                .args("describe", "--follow", "--format", "json", "--nologo", "--non-interactive", "--apphost", appHost.toString())
                .workingDirectory(appHost.parent)
                .scope(this)
                .eelIt()
        }.watchResources().map { it.toAspireResourceUpdate(appHostPath) }
    }
}