@file:Suppress("UnstableApiUsage")

package com.jetbrains.rider.aspire.unitTests

import com.intellij.openapi.client.ClientProjectSession
import com.intellij.openapi.project.Project
import com.jetbrains.rd.framework.impl.RdTask
import com.jetbrains.rd.protocol.SolutionExtListener
import com.jetbrains.rd.util.lifetime.Lifetime
import com.jetbrains.rider.aspire.generated.AspirePluginModel
import com.jetbrains.rider.aspire.generated.StartAspireHostRequest
import com.jetbrains.rider.aspire.generated.StartAspireHostResponse
import com.jetbrains.rider.aspire.generated.StopAspireHostRequest

/**
 * A listener that handles init-test-related requests received from the backend.
 *
 * Responsibilities include:
 * - Starting and stopping the Aspire host based on requests received through the model.
 * - Handling cancellations of unit test runs and stopping the Aspire host accordingly.
 */
internal class AspireUnitTestProtocolListener : SolutionExtListener<AspirePluginModel> {
    override fun extensionCreated(lifetime: Lifetime, session: ClientProjectSession, model: AspirePluginModel) {
        model.startAspireHost.set { callLifetime, request ->
            startAspireHost(callLifetime, request, session.project)
        }
        model.stopAspireHost.set { request ->
            stopAspireHost(request, session.project)
        }
        model.unitTestRunCancelled.advise(lifetime) {
            stopAspireHost(it, session.project)
        }
    }

    private fun startAspireHost(
        lifetime: Lifetime,
        request: StartAspireHostRequest,
        project: Project
    ): RdTask<StartAspireHostResponse> {
        val rdTask = RdTask<StartAspireHostResponse>()
        AspireUnitTestService.getInstance(project).startAspireHost(lifetime, request, rdTask)
        return rdTask
    }

    private fun stopAspireHost(request: StopAspireHostRequest, project: Project): RdTask<Unit> {
        val rdTask = RdTask<Unit>()
        AspireUnitTestService.getInstance(project).stopAspireHost(request, rdTask)
        return rdTask
    }

    private fun stopAspireHost(unitTestRunId: String, project: Project) {
        AspireUnitTestService.getInstance(project).stopAspireHost(unitTestRunId)
    }
}