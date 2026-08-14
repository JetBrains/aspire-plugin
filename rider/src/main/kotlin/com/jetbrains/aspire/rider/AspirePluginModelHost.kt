package com.jetbrains.aspire.rider

import com.intellij.openapi.client.ClientProjectSession
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.jetbrains.aspire.rider.generated.AspirePluginModel
import com.jetbrains.rd.protocol.SolutionExtListener
import com.jetbrains.rd.util.lifetime.Lifetime
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Duration

/**
 * Makes the backend's [AspirePluginModel] awaitable, for callers that can run before the solution has finished
 * loading.
 *
 * Reading `project.solution.aspirePluginModel` throws while the protocol extension does not exist yet, and the
 * usual trigger for [com.jetbrains.aspire.rider.debugger.DotNetAppHostEntryPointLocator] — an Aspire (CLI)
 * configuration that is already selected at IDE startup — fires exactly then.
 */
@Service(Service.Level.PROJECT)
internal class AspirePluginModelHost {
    companion object {
        fun getInstance(project: Project): AspirePluginModelHost = project.service()
    }

    private val model = CompletableDeferred<AspirePluginModel>()

    /**
     * Suspends until the backend model exists, or returns `null` when it does not appear within [timeout]
     * (e.g. in a project that never loads a .NET solution).
     */
    suspend fun awaitModel(timeout: Duration): AspirePluginModel? = withTimeoutOrNull(timeout) { model.await() }

    private fun modelCreated(model: AspirePluginModel) {
        this.model.complete(model)
    }

    internal class ProtocolListener : SolutionExtListener<AspirePluginModel> {
        override fun extensionCreated(lifetime: Lifetime, session: ClientProjectSession, model: AspirePluginModel) {
            getInstance(session.project).modelCreated(model)
        }
    }
}
