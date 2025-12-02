package com.jetbrains.aspire.sessions

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import org.jetbrains.annotations.ApiStatus
import java.util.concurrent.ConcurrentHashMap

/**
 * Stores a preferred launch mode for a particular project path.
 *
 * The preferred launch mode is a one-time setting. After getting value for a specific project, the value will be deleted.
 */
@ApiStatus.Internal
@Service(Service.Level.PROJECT)
class SessionLaunchPreferenceService {
    companion object {
        fun getInstance(project: Project): SessionLaunchPreferenceService = project.service()
    }

    private val preferences = ConcurrentHashMap<String, SessionLaunchMode>()

    fun getPreferredLaunchMode(projectPath: String): SessionLaunchMode? = preferences.remove(projectPath)

    fun setPreferredLaunchMode(projectPath: String, mode: SessionLaunchMode) {
        preferences[projectPath] = mode
    }
}

@ApiStatus.Internal
enum class SessionLaunchMode {
    RUN,
    DEBUG
}