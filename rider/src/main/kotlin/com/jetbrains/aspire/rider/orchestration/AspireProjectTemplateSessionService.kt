package com.jetbrains.aspire.rider.orchestration

import com.intellij.openapi.application.EDT
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.jetbrains.rider.model.RdProjectTemplateSession
import com.jetbrains.rider.model.projectTemplatesModel
import com.jetbrains.rider.projectView.projectTemplates.providers.RiderProjectTemplateProvider
import com.jetbrains.rider.protocol.protocol
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** Coordinates Aspire operations that use the project's single Rider template session. */
@Service(Service.Level.PROJECT)
internal class AspireProjectTemplateSessionService(private val project: Project) {
    companion object {
        fun getInstance(project: Project): AspireProjectTemplateSessionService = project.service()
    }

    private val sessionMutex = Mutex()

    suspend fun <T> withSession(action: suspend (RdProjectTemplateSession) -> T): T? = sessionMutex.withLock {
        val model = project.protocol.projectTemplatesModel
        val session = RiderProjectTemplateProvider.createSession(createSolution = false, useCachedTemplates = false)
        try {
            val attached = withContext(Dispatchers.EDT) {
                if (model.session.value != null) return@withContext false
                model.session.set(session)
                true
            }
            if (attached) action(session) else null
        } finally {
            withContext(NonCancellable + Dispatchers.EDT) {
                if (model.session.value === session) model.session.set(null)
            }
        }
    }
}
