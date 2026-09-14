package com.jetbrains.aspire.worker

import com.intellij.openapi.project.Project
import com.jetbrains.aspire.extensions.AspireAppHostLauncher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal class AspireAppHostLifecycleManagerImpl(private val project: Project) : AspireAppHostLifecycleManager {
    override suspend fun launchAppHost(appHostId: AspireAppHostId, debug: Boolean) {
        val appHost = findAppHost(appHostId) ?: return
        withContext(Dispatchers.Default) {
            AspireAppHostLauncher.getInstance()?.launchAppHost(appHost, debug, project)
        }
    }

    override suspend fun stopAppHost(appHostId: AspireAppHostId) {
        val appHost = findAppHost(appHostId) ?: return
        withContext(Dispatchers.Default) {
            AspireAppHostLauncher.getInstance()?.stopAppHost(appHost, project)
        }
    }

    private fun findAppHost(appHostId: AspireAppHostId): AspireAppHost? =
        AspireWorker.getInstance(project).appHosts.value.firstOrNull {
            it.mainFilePath.toAspireAppHostId() == appHostId
        }
}