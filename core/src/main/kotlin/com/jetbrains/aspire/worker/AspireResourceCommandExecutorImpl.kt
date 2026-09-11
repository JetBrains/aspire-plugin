package com.jetbrains.aspire.worker

import com.intellij.openapi.project.Project
import com.jetbrains.aspire.util.findResource

internal class AspireResourceCommandExecutorImpl(private val project: Project) : AspireResourceCommandExecutor {
    override suspend fun executeCommand(resourceId: AspireResourceId, commandName: String) {
        val resource = findResource(resourceId) ?: return
        resource.executeCommand(commandName)
    }

    private fun findAppHost(appHostId: AspireAppHostId): AspireAppHost? =
        AspireWorker.getInstance(project).appHosts.value.firstOrNull {
            it.mainFilePath.toAspireAppHostId() == appHostId
        }

    private fun findResource(resourceId: AspireResourceId): AspireResource? =
        findAppHost(resourceId.appHostId)?.findResource {
            it.resourceName == resourceId.resourceName
        }
}
