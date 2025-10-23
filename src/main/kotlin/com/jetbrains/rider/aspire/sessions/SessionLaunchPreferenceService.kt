package com.jetbrains.rider.aspire.sessions

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import java.util.concurrent.ConcurrentHashMap

/**
 * Stores a preferred launch mode for a particular project path.
 * If a preference is set for a project path, [SessionProcessLauncher] should use it
 * instead of the value provided by [com.jetbrains.rider.aspire.generated.CreateSessionRequest.debug].
 */
@Service(Service.Level.PROJECT)
internal class SessionLaunchPreferenceService() {
    companion object {
        fun getInstance(project: Project): SessionLaunchPreferenceService = project.service()
    }

    private val preferences = ConcurrentHashMap<String, SessionLaunchMode>()

    fun getPreferredLaunchMode(projectPath: String): SessionLaunchMode? = preferences[projectPath]

    fun setPreferredLaunchMode(projectPath: String, mode: SessionLaunchMode) {
        preferences[projectPath] = mode
    }

    fun clearPreferredLaunchMode(projectPath: String) {
        preferences.remove(projectPath)
    }
}

internal enum class SessionLaunchMode {
    RUN,
    DEBUG
}