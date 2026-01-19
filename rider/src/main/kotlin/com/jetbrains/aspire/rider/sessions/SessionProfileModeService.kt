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
import kotlin.io.path.absolutePathString

/**
 * Service responsible for storing the debug mode state of session profiles.
 *
 * This allows understanding if a particular session profile was launched under the debugger.
 *
 * @see com.jetbrains.aspire.rider.sessions.projectLaunchers.DotNetSessionProfile
 */
@Service(Service.Level.PROJECT)
internal class SessionProfileModeService(private val project: Project) {
    companion object {
        fun getInstance(project: Project): SessionProfileModeService = project.service()
    }

    private val sessionProfiles: ConcurrentMap<String, Boolean> = ConcurrentHashMap()

    fun isSessionProfileUnderDebugger(projectPath: String): Boolean? = sessionProfiles[projectPath]

    private fun saveSessionProfileMode(projectPath: String, isDebugMode: Boolean) {
        sessionProfiles[projectPath] = isDebugMode
    }

    private fun removeSessionProfileMode(projectPath: String) {
        sessionProfiles.remove(projectPath)
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
                getInstance(project)
                    .saveSessionProfileMode(profile.projectPath.absolutePathString(), profile.isDebugMode)
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
                getInstance(project)
                    .removeSessionProfileMode(profile.projectPath.absolutePathString())
            }
        }
    }
}