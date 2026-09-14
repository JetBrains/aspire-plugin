package com.jetbrains.aspire.worker

import com.intellij.openapi.project.Project
import kotlinx.coroutines.flow.StateFlow

internal class AspireServicesModelProviderImpl(project: Project) : AspireServicesModelProvider {
    override val appHosts: StateFlow<List<AspireAppHostModel>> = AspireWorker.getInstance(project).appHosts
}
