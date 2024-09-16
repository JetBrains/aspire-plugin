package com.jetbrains.rider.aspire.sessionHost.projectLaunchers

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.jetbrains.rd.util.lifetime.Lifetime
import com.jetbrains.rider.aspire.generated.SessionModel
import com.jetbrains.rider.aspire.sessionHost.SessionEvent
import kotlinx.coroutines.flow.MutableSharedFlow

@Service(Service.Level.PROJECT)
class WasmHostProjectLauncher(private val project: Project): ProjectLauncher {
    companion object {
        fun getInstance(project: Project) = project.service<WasmHostProjectLauncher>()

        private val LOG = logger<WasmHostProjectLauncher>()
    }

    override suspend fun launchRunSession(
        sessionId: String,
        sessionModel: SessionModel,
        sessionLifetime: Lifetime,
        sessionEvents: MutableSharedFlow<SessionEvent>
    ) {

    }

    override suspend fun launchDebugSession(
        sessionId: String,
        sessionModel: SessionModel,
        sessionLifetime: Lifetime,
        sessionEvents: MutableSharedFlow<SessionEvent>
    ) {

    }
}