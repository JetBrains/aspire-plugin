@file:Suppress("UnstableApiUsage")

package com.jetbrains.aspire.util

import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.util.ExecUtil
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.platform.ide.progress.withBackgroundProgress
import com.intellij.util.execution.ParametersListUtil
import com.jetbrains.rider.model.RdTargetFrameworkId
import com.jetbrains.rider.model.RdVersionInfo
import com.jetbrains.rider.runtime.RiderDotNetActiveRuntimeHost
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.nio.file.Path
import kotlin.io.path.Path
import kotlin.io.path.absolutePathString
import kotlin.io.path.nameWithoutExtension

@Service(Service.Level.PROJECT)
class MSBuildPropertyService(private val project: Project) {
    companion object {
        fun getInstance(project: Project) = project.service<MSBuildPropertyService>()

        private val LOG = logger<MSBuildPropertyService>()

        private val json by lazy {
            Json { ignoreUnknownKeys = true }
        }
    }

    suspend fun getProjectRunProperties(projectPath: Path): ProjectRunProperties? {
        val projectRunPropertiesJson = getProjectProperties(
            projectPath,
            "TargetFramework,RunCommand,RunArguments,RunWorkingDirectory"
        ) ?: return null
        val projectProperties = json.decodeFromString<ProjectRunPropertiesOutput>(projectRunPropertiesJson)

        val targetFramework = getTargetFrameworkId(projectProperties.properties.targetFramework)
            ?: return null
        val executable = getExecutablePath(projectProperties.properties.runCommand)
            ?: return null
        val args = ParametersListUtil.parse(projectProperties.properties.runArguments)
        val workingDirectory = getWorkingDirectoryPath(projectProperties.properties.runWorkingDirectory)
            ?: return null

        return ProjectRunProperties(
            targetFramework,
            executable,
            args,
            workingDirectory
        )
    }

    suspend fun getProjectTargetFramework(projectPath: Path): RdTargetFrameworkId? {
        val targetFramework = getProjectProperties(
            projectPath,
            "TargetFramework"
        )?.trim() ?: return null

        return getTargetFrameworkId(targetFramework)
    }

    private suspend fun getProjectProperties(projectPath: Path, listOfProperties: String): String? {
        val runtime = RiderDotNetActiveRuntimeHost.getInstance(project).dotNetCoreRuntime.value
        if (runtime == null) {
            LOG.warn("Unable to find active .NET runtime")
            return null
        }

        val buildCommandLine = GeneralCommandLine()
            .withExePath(runtime.cliExePath.absolutePathString())
            .withParameters(listOf("build", projectPath.absolutePathString()))
        val buildOutput = withContext(Dispatchers.IO) {
            withBackgroundProgress(project, "Building ${projectPath.nameWithoutExtension}") {
                ExecUtil.execAndGetOutput(buildCommandLine)
            }
        }
        if (!buildOutput.checkSuccess(LOG)) {
            LOG.warn("Unable to build project ${projectPath.absolutePathString()}")
            return null
        }

        val propertyCommandLine = GeneralCommandLine()
            .withExePath(runtime.cliExePath.absolutePathString())
            .withParameters(
                listOf(
                    "build",
                    projectPath.absolutePathString(),
                    "-getProperty:${listOfProperties}"
                )
            )
        val propertyOutput = withContext(Dispatchers.IO) {
            ExecUtil.execAndGetOutput(propertyCommandLine)
        }
        if (!propertyOutput.checkSuccess(LOG)) {
            LOG.warn("Unable to get properties for project ${projectPath.absolutePathString()}")
            return null
        }

        return propertyOutput.stdout
    }

    private fun getTargetFrameworkId(targetFramework: String): RdTargetFrameworkId? {
        val targetFrameworkVersion = targetFramework.removePrefix("net")
        val versionParts = targetFrameworkVersion.split('.').map { it.toIntOrNull() }

        if (versionParts.any { it == null }) {
            LOG.warn("Unable to parse target framework $targetFramework")
            return null
        }

        val versionInfo = when (versionParts.size) {
            1 -> {
                RdVersionInfo(versionParts[0]!!, 0, 0)
            }

            2 -> {
                RdVersionInfo(versionParts[0]!!, versionParts[1]!!, 0)
            }

            3 -> {
                RdVersionInfo(versionParts[0]!!, versionParts[1]!!, versionParts[2]!!)
            }

            else -> {
                LOG.warn("Unable to parse target framework $targetFramework")
                return null
            }
        }

        return RdTargetFrameworkId(
            versionInfo,
            ".NETCoreApp",
            targetFramework,
            isNetCoreApp = true,
            isNetFramework = false
        )
    }

    private fun getExecutablePath(runCommand: String): Path? {
        if (runCommand.isEmpty()) {
            LOG.warn("MSBuild RunCommand is empty")
            return null
        }

        val runCommandPath = Path(runCommand)
        val runCommandExecutable = runCommandPath.nameWithoutExtension

        if (runCommandExecutable.equals("dotnet", true)) {
            LOG.warn("Unable to execute MSBuild RunCommand with .NET CLI")
            return null
        }

        if (!runCommandPath.isAbsolute) {
            LOG.warn("MSBuild RunCommand is not absolute path: $runCommandPath")
            return null
        }

        return runCommandPath
    }

    private fun getWorkingDirectoryPath(runWorkingDirectory: String): Path? {
        if (runWorkingDirectory.isEmpty()) {
            LOG.warn("MSBuild RunWorkingDirectory is empty")
            return null
        }

        val runWorkingDirectoryPath = Path(runWorkingDirectory)
        if (!runWorkingDirectoryPath.isAbsolute) {
            LOG.warn("MSBuild RunWorkingDirectory is not absolute path: $runWorkingDirectory")
            return null
        }

        return runWorkingDirectoryPath
    }

    data class ProjectRunProperties(
        val targetFramework: RdTargetFrameworkId,
        val executablePath: Path,
        val arguments: List<String>,
        val workingDirectory: Path
    )
}