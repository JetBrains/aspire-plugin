package com.jetbrains.aspire.rider.resources

import com.intellij.notification.Notification
import com.intellij.notification.NotificationType
import com.intellij.openapi.application.EDT
import com.intellij.openapi.client.ClientProjectSession
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.jetbrains.aspire.rider.AspireRiderBundle
import com.jetbrains.aspire.rider.generated.AspirePluginModel
import com.jetbrains.aspire.rider.generated.ExecuteResourceCommandRequest
import com.jetbrains.aspire.util.findResource
import com.jetbrains.aspire.worker.AspireResource
import com.jetbrains.aspire.worker.AspireWorker
import com.jetbrains.rd.protocol.SolutionExtListener
import com.jetbrains.rd.util.lifetime.Lifetime
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal class RiderResourceProtocolListener : SolutionExtListener<AspirePluginModel> {
    companion object {
        private val LOG = logger<RiderResourceProtocolListener>()
    }

    @Suppress("UnstableApiUsage")
    override fun extensionCreated(lifetime: Lifetime, session: ClientProjectSession, model: AspirePluginModel) {
        model.executeResourceCommand.advise(lifetime) { request ->
            lifetime.coroutineScope.launch {
                val resource = findResource(session.project, request)
                if (resource == null) {
                    LOG.warn("Unable to find Aspire resource '${request.resourceName}' to execute '${request.commandName}'")
                    notifyAboutMissingResource(session.project, request.resourceName)
                    return@launch
                }

                resource.executeCommand(request.commandName)
            }
        }
    }

    private fun findResource(project: Project, request: ExecuteResourceCommandRequest): AspireResource? {
        return findResource(project) { resource ->
            resource.resourceState.value.name == request.resourceName
        } ?: findResource(project) { resource ->
            resource.resourceState.value.displayName == request.resourceName
        }
    }

    private fun findResource(project: Project, predicate: (AspireResource) -> Boolean): AspireResource? {
        return AspireWorker.getInstance(project)
            .appHosts
            .value
            .firstNotNullOfOrNull { appHost -> appHost.findResource(predicate) }
    }

    private suspend fun notifyAboutMissingResource(project: Project, resourceName: String) = withContext(Dispatchers.EDT) {
        Notification(
            "Aspire",
            AspireRiderBundle.message("notification.unable.to.find.aspire.resource"),
            AspireRiderBundle.message("notification.aspire.resource.not.found.description", resourceName),
            NotificationType.WARNING
        ).notify(project)
    }
}
