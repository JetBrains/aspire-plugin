package com.jetbrains.rider.aspire.sessionHost

import com.intellij.openapi.application.readAction
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.util.execution.ParametersListUtil
import com.intellij.util.io.systemIndependentPath
import com.jetbrains.rider.aspire.generated.SessionEnvironmentVariable
import com.jetbrains.rider.aspire.generated.SessionModel
import com.jetbrains.rider.aspire.util.MSBuildPropertyService
import com.jetbrains.rider.model.RunnableProject
import com.jetbrains.rider.model.runnableProjectsModel
import com.jetbrains.rider.projectView.solution
import com.jetbrains.rider.run.configurations.launchSettings.LaunchSettingsJson
import com.jetbrains.rider.run.configurations.launchSettings.LaunchSettingsJsonService
import com.jetbrains.rider.runtime.DotNetExecutable
import com.jetbrains.rider.runtime.dotNetCore.DotNetCoreRuntimeType
import java.nio.file.Path
import kotlin.io.path.Path

@Service(Service.Level.PROJECT)
class SessionExecutableFactory(private val project: Project) {
    companion object {
        fun getInstance(project: Project) = project.service<SessionExecutableFactory>()
    }

    suspend fun createExecutable(sessionModel: SessionModel): DotNetExecutable? {
        val sessionProjectPath = Path(sessionModel.projectPath)
        val runnableProject = project.solution.runnableProjectsModel.findBySessionProject(sessionProjectPath)

        return if (runnableProject != null) {
            getExecutableForRunnableProject(runnableProject, sessionModel)
        } else {
            getExecutableForExternalProject(sessionProjectPath, sessionModel)
        }
    }

    private suspend fun getExecutableForRunnableProject(
        runnableProject: RunnableProject,
        sessionModel: SessionModel
    ): DotNetExecutable? {
        val output = runnableProject.projectOutputs.firstOrNull() ?: return null
        val launchProfile = sessionModel.launchProfile?.let { getLaunchProfile(it, runnableProject) }
        val executablePath = output.exePath
        val workingDirectory = output.workingDirectory
        val arguments = mergeArguments(sessionModel.args, output.defaultArguments, launchProfile?.commandLineArgs)
        val envs = mergeEnvironmentVariables(sessionModel.envs, launchProfile?.environmentVariables)

        return DotNetExecutable(
            executablePath,
            output.tfm,
            workingDirectory,
            arguments,
            false,
            false,
            envs,
            false,
            { _, _, _ -> },
            null,
            "",
            !executablePath.endsWith(".dll", true),
            DotNetCoreRuntimeType
        )
    }

    private suspend fun getLaunchProfile(
        launchProfile: String,
        runnableProject: RunnableProject
    ): LaunchSettingsJson.Profile? {
        if (launchProfile.isEmpty()) return null

        val launchSettings = readAction {
            LaunchSettingsJsonService.loadLaunchSettings(runnableProject)
        } ?: return null

        return launchSettings.profiles?.get(launchProfile)
    }

    private suspend fun getExecutableForExternalProject(
        sessionProjectPath: Path,
        sessionModel: SessionModel
    ): DotNetExecutable? {
        val propertyService = MSBuildPropertyService.getInstance(project)
        val properties = propertyService.getProjectRunProperties(sessionProjectPath) ?: return null
        val launchProfile = sessionModel.launchProfile?.let { getLaunchProfile(it, sessionProjectPath) }
        val executablePath = properties.executablePath.systemIndependentPath
        val workingDirectory = properties.workingDirectory.systemIndependentPath
        val arguments = mergeArguments(sessionModel.args, properties.arguments, launchProfile?.commandLineArgs)
        val envs = mergeEnvironmentVariables(sessionModel.envs, launchProfile?.environmentVariables)

        return DotNetExecutable(
            executablePath,
            properties.targetFramework,
            workingDirectory,
            arguments,
            false,
            false,
            envs,
            false,
            { _, _, _ -> },
            null,
            "",
            !executablePath.endsWith(".dll", true),
            DotNetCoreRuntimeType
        )
    }

    private suspend fun getLaunchProfile(launchProfile: String, sessionProjectPath: Path): LaunchSettingsJson.Profile? {
        if (launchProfile.isEmpty()) return null

        val launchSettingsFile =
            LaunchSettingsJsonService.getLaunchSettingsFileForProject(sessionProjectPath.toFile()) ?: return null
        val launchSettings = readAction {
            LaunchSettingsJsonService.loadLaunchSettings(launchSettingsFile)
        } ?: return null

        return launchSettings.profiles?.get(launchProfile)
    }

    private fun mergeArguments(
        sessionArguments: Array<String>?,
        defaultArguments: List<String>,
        launchProfileArguments: String?
    ) = buildString {
        if (sessionArguments?.isNotEmpty() == true) {
            append(ParametersListUtil.join(sessionArguments.toList()))
            append(" ")
        }

        if (defaultArguments.isNotEmpty()) {
            append(ParametersListUtil.join(defaultArguments))
            append(" ")
        }

        if (!launchProfileArguments.isNullOrEmpty()) {
            append(launchProfileArguments)
        }
    }

    private fun mergeEnvironmentVariables(
        sessionEnvironmentVariables: Array<SessionEnvironmentVariable>?,
        launchProfileEnvironmentVariables: Map<String, String?>?
    ) = buildMap {
        if (launchProfileEnvironmentVariables?.isNotEmpty() == true) {
            launchProfileEnvironmentVariables.forEach {
                it.value?.let { value -> put(it.key, value) }
            }
        }

        if (sessionEnvironmentVariables?.isNotEmpty() == true) {
            sessionEnvironmentVariables.associateTo(this) { it.key to it.value }
        }
    }
}