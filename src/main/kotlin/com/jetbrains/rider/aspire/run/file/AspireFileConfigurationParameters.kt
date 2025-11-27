package com.jetbrains.rider.aspire.run.file

import com.intellij.execution.configuration.EnvironmentVariablesComponent
import com.intellij.execution.configurations.RuntimeConfigurationError
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.JDOMExternalizerUtil
import com.jetbrains.rd.util.reactive.hasTrueValue
import com.jetbrains.rider.aspire.run.AspireRunConfigurationParameters
import com.jetbrains.rider.projectView.solution
import com.jetbrains.rider.run.RiderRunBundle
import com.jetbrains.rider.run.configurations.project.DotNetProjectConfigurationParameters
import com.jetbrains.rider.run.configurations.project.DotNetStartBrowserParameters
import org.jdom.Element
import kotlin.collections.hashMapOf
import kotlin.io.path.Path
import kotlin.io.path.exists
import kotlin.io.path.isDirectory

/**
 * Parameters for file-based Aspire run configuration.
 */
internal class AspireFileConfigurationParameters(
    private val project: Project,
    var filePath: String,
    var profileName: String,
    var trackArguments: Boolean,
    var arguments: String,
    var trackWorkingDirectory: Boolean,
    var workingDirectory: String,
    var trackEnvs: Boolean,
    var envs: Map<String, String>,
    override var usePodmanRuntime: Boolean,
    var trackUrl: Boolean,
    var trackBrowserLaunch: Boolean,
    override var startBrowserParameters: DotNetStartBrowserParameters
) : AspireRunConfigurationParameters {
    companion object {
        private const val FILE_PATH = "FILE_PATH"
        private const val LAUNCH_PROFILE_NAME = "LAUNCH_PROFILE_NAME"
        private const val TRACK_ARGUMENTS = "TRACK_ARGUMENTS"
        private const val ARGUMENTS = "ARGUMENTS"
        private const val TRACK_WORKING_DIRECTORY = "TRACK_WORKING_DIRECTORY"
        private const val WORKING_DIRECTORY = "WORKING_DIRECTORY"
        private const val TRACK_ENVS = "TRACK_ENVS"
        private const val TRACK_URL = "TRACK_URL"
        private const val TRACK_BROWSER_LAUNCH = "TRACK_BROWSER_LAUNCH"
        private const val USE_PODMAN_RUNTIME = "USE_PODMAN_RUNTIME"

        fun createDefault(project: Project) = AspireFileConfigurationParameters(
            project,
            filePath = "",
            profileName = "",
            trackArguments = true,
            arguments = "",
            trackWorkingDirectory = true,
            workingDirectory = "",
            trackEnvs = true,
            envs = hashMapOf(),
            trackUrl = true,
            trackBrowserLaunch = true,
            usePodmanRuntime = false,
            startBrowserParameters = DotNetStartBrowserParameters()
        )
    }

    override val mainFilePath: String
        get() = filePath

    fun validate() {
        if (!project.solution.isLoaded.hasTrueValue) {
            throw RuntimeConfigurationError(DotNetProjectConfigurationParameters.SOLUTION_IS_LOADING)
        }

        if (filePath.isEmpty()) {
            throw RuntimeConfigurationError(DotNetProjectConfigurationParameters.PROJECT_NOT_SPECIFIED)
        }

//        if (profileName.isEmpty()) {
//            throw RuntimeConfigurationError(RiderRunBundle.message("launch.profile.is.not.specified"))
//        }

        if (!trackWorkingDirectory) {
            val workingDirectoryPath = Path(workingDirectory)
            if (!workingDirectoryPath.exists() || !workingDirectoryPath.isDirectory())
                throw RuntimeConfigurationError(
                    RiderRunBundle.message(
                        "dialog.message.invalid.working.dir",
                        workingDirectory.ifEmpty { "<empty>" }
                    )
                )
        }
    }

    fun readExternal(element: Element) {
        filePath = JDOMExternalizerUtil.readField(element, FILE_PATH) ?: ""
        profileName = JDOMExternalizerUtil.readField(element, LAUNCH_PROFILE_NAME) ?: ""
        val trackArgumentsString = JDOMExternalizerUtil.readField(element, TRACK_ARGUMENTS) ?: ""
        trackArguments = trackArgumentsString != "0"
        arguments = JDOMExternalizerUtil.readField(element, ARGUMENTS) ?: ""
        val trackWorkingDirectoryString = JDOMExternalizerUtil.readField(element, TRACK_WORKING_DIRECTORY) ?: ""
        trackWorkingDirectory = trackWorkingDirectoryString != "0"
        workingDirectory = JDOMExternalizerUtil.readField(element, WORKING_DIRECTORY) ?: ""
        val trackEnvsString = JDOMExternalizerUtil.readField(element, TRACK_ENVS) ?: ""
        trackEnvs = trackEnvsString != "0"
        EnvironmentVariablesComponent.readExternal(element, envs)
        val usePodmanRuntimeString = JDOMExternalizerUtil.readField(element, USE_PODMAN_RUNTIME) ?: ""
        usePodmanRuntime = usePodmanRuntimeString == "1"
        val trackUrlString = JDOMExternalizerUtil.readField(element, TRACK_URL) ?: ""
        trackUrl = trackUrlString != "0"
        val trackBrowserLaunchString = JDOMExternalizerUtil.readField(element, TRACK_BROWSER_LAUNCH) ?: ""
        trackBrowserLaunch = trackBrowserLaunchString != "0"
        startBrowserParameters = DotNetStartBrowserParameters.readExternal(element)
    }

    fun writeExternal(element: Element) {
        JDOMExternalizerUtil.writeField(element, FILE_PATH, filePath)
        JDOMExternalizerUtil.writeField(element, LAUNCH_PROFILE_NAME, profileName)
        JDOMExternalizerUtil.writeField(element, TRACK_ARGUMENTS, if (trackArguments) "1" else "0")
        JDOMExternalizerUtil.writeField(element, ARGUMENTS, arguments)
        JDOMExternalizerUtil.writeField(element, TRACK_WORKING_DIRECTORY, if (trackWorkingDirectory) "1" else "0")
        JDOMExternalizerUtil.writeField(element, WORKING_DIRECTORY, workingDirectory)
        JDOMExternalizerUtil.writeField(element, TRACK_ENVS, if (trackEnvs) "1" else "0")
        EnvironmentVariablesComponent.writeExternal(element, envs)
        JDOMExternalizerUtil.writeField(element, TRACK_URL, if (trackUrl) "1" else "0")
        JDOMExternalizerUtil.writeField(element, TRACK_BROWSER_LAUNCH, if (trackBrowserLaunch) "1" else "0")
        JDOMExternalizerUtil.writeField(element, USE_PODMAN_RUNTIME, if (usePodmanRuntime) "1" else "0")
        startBrowserParameters.writeExternal(element)
    }

    fun copy() = AspireFileConfigurationParameters(
        project,
        filePath,
        profileName,
        trackArguments,
        arguments,
        trackWorkingDirectory,
        workingDirectory,
        trackEnvs,
        envs,
        usePodmanRuntime,
        trackUrl,
        trackBrowserLaunch,
        startBrowserParameters.copy()
    )
}
