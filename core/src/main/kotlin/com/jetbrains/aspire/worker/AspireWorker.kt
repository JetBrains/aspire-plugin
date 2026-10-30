@file:Suppress("UnstableApiUsage")

package com.jetbrains.aspire.worker

import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.diagnostic.trace
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.jetbrains.aspire.dcp.AspireSessionServerEndpoint
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

        private fun generateRandomAppHostId(): AspireAppHostId {
            val allowedChars = buildList {
                addAll('A'..'Z')
                addAll('a'..'z')
                addAll('0'..'9')
            }
            val id = (1..5)
                .map { allowedChars.random() }
                .joinToString("")
            return AspireAppHostId(id)
        }

        private fun generateUniqueAppHostId(
            registeredIds: Set<AspireAppHostId>,
            nextCandidate: () -> AspireAppHostId = ::generateRandomAppHostId,
        ): AspireAppHostId {
            while (true) {
                val candidate = nextCandidate()
                if (candidate !in registeredIds) return candidate
            }
        }
    }

    private val _appHosts: MutableStateFlow<List<AspireAppHost>> = MutableStateFlow(emptyList())
    val appHosts: StateFlow<List<AspireAppHost>> = _appHosts.asStateFlow()

    private fun addAppHost(name: String, appHostPath: Path) {
        LOG.trace { "Adding a new Aspire AppHost ${appHostPath.absolutePathString()}" }

        _appHosts.update { currentList ->
            if (currentList.any { it.appHostPath == appHostPath }) return@update currentList

            val id = generateUniqueAppHostId(currentList.mapTo(mutableSetOf()) { it.id })
            val appHost = AspireAppHost(id, name, appHostPath, project, cs)
            Disposer.register(this@AspireWorker, appHost)
            currentList + appHost
        }
    }

    private fun removeAppHostByPath(appHostPath: Path) {
        LOG.trace { "Removing the Aspire AppHost ${appHostPath.absolutePathString()}" }

        _appHosts.update { currentList ->
            currentList.filter { it.appHostPath != appHostPath }
        }
    }

    fun getAppHostById(appHostId: AspireAppHostId): AspireAppHost? =
        _appHosts.value.firstOrNull { it.id == appHostId }

    /** Starts or reuses the AppHost's session server and returns the DCP connection environment variables. */
    suspend fun startAppHostSessionServer(appHostPath: Path): Pair<AspireAppHost, AspireSessionServerEndpoint> {
        val appHost = requireNotNull(getOrCreateAppHostByPath(appHostPath))
        val endpoint = appHost.startSessionServer()

        return appHost to endpoint
    }

    private fun getOrCreateAppHostByPath(appHostPath: Path): AspireAppHost? {
        _appHosts.value.firstOrNull { it.appHostPath == appHostPath }?.let { return it }

        addAppHost(appHostPath.nameWithoutExtension, appHostPath)

        return _appHosts.value.firstOrNull { it.appHostPath == appHostPath }
    }

    override fun dispose() {
    }

    private class DetectionListener(private val project: Project) : AppHostDetectionListener {
        override fun appHostDetected(appHostName: String, appHostFilePath: Path) {
            getInstance(project).addAppHost(appHostName, appHostFilePath)
        }

        override fun appHostRemoved(appHostFilePath: Path) {
            getInstance(project).removeAppHostByPath(appHostFilePath)
        }
    }
}
