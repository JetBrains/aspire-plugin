package com.jetbrains.aspire.rider.sessions

import com.intellij.execution.ExecutionListener
import com.intellij.execution.process.ProcessHandler
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import com.intellij.openapi.components.service
import com.jetbrains.aspire.rider.sessions.projectLaunchers.DotNetSessionProfile
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentMap

@Service(Service.Level.PROJECT)
internal class SessionProfileModeService(private val project: Project) {
    companion object {
        fun getInstance(project: Project): SessionProfileModeService = project.service()
    }

    private val sessionProfiles: ConcurrentMap<String, Boolean> = ConcurrentHashMap()

    fun isSessionProfileUnderDebugger(sessionId: String): Boolean? = sessionProfiles[sessionId]

    private fun saveSessionProfileMode(profile: DotNetSessionProfile) {
        sessionProfiles[profile.sessionId] = profile.isDebugMode
    }

    private fun removeSessionProfileMode(profile: DotNetSessionProfile) {
        sessionProfiles.remove(profile.sessionId)
    }

    private class Listener(private val project: Project) : ExecutionListener {
        override fun processStarted(
            executorId: String,
            env: ExecutionEnvironment,
            handler: ProcessHandler
        ) {
            val profile = env.runProfile
            if (profile is DotNetSessionProfile) {
                profile.aspireHostProjectPath ?: return
                getInstance(project).saveSessionProfileMode(profile)
            }
        }

        override fun processTerminated(
            executorId: String,
            env: ExecutionEnvironment,
            handler: ProcessHandler,
            exitCode: Int
        ) {
            val profile = env.runProfile
            if (profile is DotNetSessionProfile) {
                profile.aspireHostProjectPath ?: return
                getInstance(project).removeSessionProfileMode(profile)
            }
        }
    }
}