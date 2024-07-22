@file:Suppress("UnstableApiUsage")

package me.rafaelldi.aspire.unitTests

import com.intellij.openapi.client.ClientProjectSession
import com.intellij.openapi.project.Project
import com.jetbrains.rd.framework.impl.RdTask
import com.jetbrains.rd.protocol.SolutionExtListener
import com.jetbrains.rd.util.lifetime.Lifetime
import me.rafaelldi.aspire.generated.AspirePluginModel
import me.rafaelldi.aspire.generated.SessionHostModel

class AspireUnitTestProtocolListener : SolutionExtListener<AspirePluginModel> {
    override fun extensionCreated(lifetime: Lifetime, session: ClientProjectSession, model: AspirePluginModel) {
        model.startSessionHost.set { callLifetime, sessionHostModel ->
            startSessionHost(
                callLifetime,
                sessionHostModel,
                session.project
            )
        }
    }

    private fun startSessionHost(lifetime: Lifetime, model: SessionHostModel, project: Project): RdTask<Unit> {
        val rdTask = RdTask<Unit>()
        AspireUnitTestService.getInstance(project).startSessionHost(lifetime, model, rdTask)
        return rdTask
    }
}