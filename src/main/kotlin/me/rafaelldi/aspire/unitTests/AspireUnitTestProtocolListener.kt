@file:Suppress("UnstableApiUsage")

package me.rafaelldi.aspire.unitTests

import com.intellij.openapi.client.ClientProjectSession
import com.intellij.openapi.project.Project
import com.jetbrains.rd.framework.impl.RdTask
import com.jetbrains.rd.protocol.SolutionExtListener
import com.jetbrains.rd.util.lifetime.Lifetime
import me.rafaelldi.aspire.generated.AspirePluginModel
import me.rafaelldi.aspire.generated.StartSessionHostRequest
import me.rafaelldi.aspire.generated.StartSessionHostResponse
import me.rafaelldi.aspire.generated.StopSessionHostRequest

class AspireUnitTestProtocolListener : SolutionExtListener<AspirePluginModel> {
    override fun extensionCreated(lifetime: Lifetime, session: ClientProjectSession, model: AspirePluginModel) {
        model.startSessionHost.set { callLifetime, request ->
            startSessionHost(callLifetime, request, session.project)
        }
        model.stopSessionHost.set { request ->
            stopSessionHost(request, session.project)
        }
        model.unitTestRunCancelled.advise(lifetime) {
            stopSessionHost(it, session.project)
        }
    }

    private fun startSessionHost(
        lifetime: Lifetime,
        request: StartSessionHostRequest,
        project: Project
    ): RdTask<StartSessionHostResponse> {
        val rdTask = RdTask<StartSessionHostResponse>()
        AspireUnitTestService.getInstance(project).startSessionHost(lifetime, request, rdTask)
        return rdTask
    }

    private fun stopSessionHost(request: StopSessionHostRequest, project: Project): RdTask<Unit> {
        val rdTask = RdTask<Unit>()
        AspireUnitTestService.getInstance(project).stopSessionHost(request, rdTask)
        return rdTask
    }

    private fun stopSessionHost(unitTestRunId: String, project: Project) {
        AspireUnitTestService.getInstance(project).stopSessionHost(unitTestRunId)
    }
}