package com.jetbrains.aspire.rider.sessions

import com.intellij.execution.ExecutionListener
import com.intellij.execution.process.ProcessHandler
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import com.intellij.openapi.components.service
import com.jetbrains.aspire.sessions.SessionProfile
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentMap

@Service(Service.Level.PROJECT)
internal class SessionProfileService(private val project: Project) {
    companion object {
        fun getInstance(project: Project): SessionProfileService = project.service()
    }

    private val sessionProfiles: ConcurrentMap<String, SessionProfile> = ConcurrentHashMap()

    fun getSessionProfile(sessionId: String): SessionProfile? = sessionProfiles[sessionId]

    private fun saveSessionProfile(profile: SessionProfile) {
        sessionProfiles[profile.sessionId] = profile
    }

    private fun removeSessionProfile(profile: SessionProfile) {
        sessionProfiles.remove(profile.sessionId)
    }

    private class Listener(private val project: Project) : ExecutionListener {
        override fun processStarted(
            executorId: String,
            env: ExecutionEnvironment,
            handler: ProcessHandler
        ) {
            val profile = env.runProfile
            if (profile is SessionProfile) {
                profile.aspireHostProjectPath ?: return
                getInstance(project).saveSessionProfile(profile)
            }
        }

        override fun processTerminated(
            executorId: String,
            env: ExecutionEnvironment,
            handler: ProcessHandler,
            exitCode: Int
        ) {
            val profile = env.runProfile
            if (profile is SessionProfile) {
                profile.aspireHostProjectPath ?: return
                getInstance(project).removeSessionProfile(profile)
            }
        }
    }
}