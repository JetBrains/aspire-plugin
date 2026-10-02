package com.jetbrains.aspire.worker

import com.intellij.openapi.project.Project
import com.jetbrains.aspire.util.getAllResources

internal class AspireAppHostResourcesProviderImpl(private val project: Project) : AspireAppHostResourcesProvider {
    override suspend fun getResources(appHostPath: AspireAppHostPath): List<AspireResourceData> {
        val appHost = AspireWorker.getInstance(project).getAppHostById(appHostPath) ?: return emptyList()
        return appHost.getAllResources().map { it.data.value }
    }
}
