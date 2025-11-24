package com.jetbrains.rider.aspire.run.file

import com.intellij.execution.configuration.EnvironmentVariablesComponent
import com.intellij.execution.configurations.RuntimeConfigurationError
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.JDOMExternalizerUtil
import com.jetbrains.rd.util.reactive.hasTrueValue
import com.jetbrains.rider.aspire.run.AspireRunConfigurationParameters
import com.jetbrains.rider.projectView.solution
import com.jetbrains.rider.run.configurations.project.DotNetProjectConfigurationParameters
import com.jetbrains.rider.run.configurations.project.DotNetStartBrowserParameters
import org.jdom.Element
import kotlin.collections.hashMapOf

/**
 * Parameters for file-based Aspire run configuration.
 */
internal class AspireFileConfigurationParameters(
    private val project: Project,
    var filePath: String,
    var arguments: String,
    var workingDirectory: String,
    var envs: Map<String, String>,
    override var usePodmanRuntime: Boolean,
    override var startBrowserParameters: DotNetStartBrowserParameters
) : AspireRunConfigurationParameters {
    companion object {
        private const val FILE_PATH = "FILE_PATH"
        private const val ARGUMENTS = "ARGUMENTS"
        private const val WORKING_DIRECTORY = "WORKING_DIRECTORY"
        private const val USE_PODMAN_RUNTIME = "USE_PODMAN_RUNTIME"

        fun createDefault(project: Project) = AspireFileConfigurationParameters(
            project,
            filePath = "",
            arguments = "",
            workingDirectory = "",
            envs = hashMapOf(),
            usePodmanRuntime = false,
            startBrowserParameters = DotNetStartBrowserParameters()
        )
    }

    override val mainFilePath: String
        get() = filePath

    fun validate() {
        if (!project.solution.isLoaded.hasTrueValue) {
            throw RuntimeConfigurationError(DotNetProjectConfigurationParameters.Companion.SOLUTION_IS_LOADING)
        }
    }

    fun readExternal(element: Element) {
        filePath = JDOMExternalizerUtil.readField(element, FILE_PATH) ?: ""
        arguments = JDOMExternalizerUtil.readField(element, ARGUMENTS) ?: ""
        workingDirectory = JDOMExternalizerUtil.readField(element, WORKING_DIRECTORY) ?: ""
        EnvironmentVariablesComponent.readExternal(element, envs)
        val usePodmanRuntimeString = JDOMExternalizerUtil.readField(element, USE_PODMAN_RUNTIME) ?: ""
        usePodmanRuntime = usePodmanRuntimeString == "1"
        startBrowserParameters = DotNetStartBrowserParameters.readExternal(element)
    }

    fun writeExternal(element: Element) {
        JDOMExternalizerUtil.writeField(element, FILE_PATH, filePath)
        JDOMExternalizerUtil.writeField(element, ARGUMENTS, arguments)
        JDOMExternalizerUtil.writeField(element, WORKING_DIRECTORY, workingDirectory)
        EnvironmentVariablesComponent.writeExternal(element, envs)
        JDOMExternalizerUtil.writeField(element, USE_PODMAN_RUNTIME, if (usePodmanRuntime) "1" else "0")
        startBrowserParameters.writeExternal(element)
    }

    fun copy() = AspireFileConfigurationParameters(
        project,
        filePath,
        arguments,
        workingDirectory,
        envs,
        usePodmanRuntime,
        startBrowserParameters.copy()
    )
}
