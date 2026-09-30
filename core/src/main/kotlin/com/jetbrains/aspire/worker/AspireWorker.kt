@file:Suppress("UnstableApiUsage")

package com.jetbrains.aspire.worker

import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.diagnostic.trace
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import org.jetbrains.annotations.ApiStatus
import java.nio.file.Path
import kotlin.io.path.absolutePathString
import kotlin.io.path.nameWithoutExtension

/**
 * Manages the Aspire AppHost instances and their embedded session servers in a project.
 */
@ApiStatus.Internal
@Service(Service.Level.PROJECT)
class AspireWorker(private val project: Project, private val cs: CoroutineScope) : Disposable {
    companion object {
        fun getInstance(project: Project): AspireWorker = project.service()
        private val LOG = logger<AspireWorker>()
    }

    private val _appHosts: MutableStateFlow<List<AspireAppHost>> = MutableStateFlow(emptyList())
    val appHosts: StateFlow<List<AspireAppHost>> = _appHosts.asStateFlow()

    private fun addAppHost(name: String, appHostFilePath: Path) {
        LOG.trace { "Adding a new Aspire AppHost ${appHostFilePath.absolutePathString()}" }

        _appHosts.update { currentList ->
            if (currentList.any { it.mainFilePath == appHostFilePath }) return@update currentList

            val appHost = AspireAppHost(name, appHostFilePath, project, cs)
            Disposer.register(this@AspireWorker, appHost)
            currentList + appHost
        }
    }

    private fun removeAppHost(appHostFilePath: Path) {
        LOG.trace { "Removing the Aspire AppHost ${appHostFilePath.absolutePathString()}" }

        _appHosts.update { currentList ->
            currentList.filter { it.mainFilePath != appHostFilePath }
        }
    }

    fun getAppHostById(appHostId: AspireAppHostId): AspireAppHost? =
        _appHosts.value.firstOrNull { it.mainFilePath.toAspireAppHostId() == appHostId }

    fun getOrCreateAppHostByPath(appHostFilePath: Path): AspireAppHost? {
        _appHosts.value.firstOrNull { it.mainFilePath == appHostFilePath }?.let { return it }

        addAppHost(appHostFilePath.nameWithoutExtension, appHostFilePath)

        return _appHosts.value.firstOrNull { it.mainFilePath == appHostFilePath }
    }

    override fun dispose() {
    }

    private class DetectionListener(private val project: Project) : AppHostDetectionListener {
        override fun appHostDetected(appHostName: String, appHostFilePath: Path) {
            getInstance(project).addAppHost(appHostName, appHostFilePath)
        }

        override fun appHostRemoved(appHostFilePath: Path) {
            getInstance(project).removeAppHost(appHostFilePath)
        }
    }
}
