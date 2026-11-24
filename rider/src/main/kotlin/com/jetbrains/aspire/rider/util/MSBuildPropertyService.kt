@file:Suppress("UnstableApiUsage")

package com.jetbrains.aspire.rider.util

import com.intellij.execution.ExecutionException
import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.util.ExecUtil
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.io.NioFiles
import com.intellij.platform.ide.progress.withBackgroundProgress
import com.jetbrains.rider.model.RdTargetFrameworkId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.absolutePathString
import kotlin.io.path.extension
import kotlin.io.path.nameWithoutExtension

@Service(Service.Level.PROJECT)
internal class MSBuildPropertyService(private val project: Project) {
    companion object {
        fun getInstance(project: Project) = project.service<MSBuildPropertyService>()

        private const val RUN_PROPERTIES = "TargetFramework,TargetPath,RunCommand,RunArguments,RunWorkingDirectory"

        private val json by lazy {
            Json { ignoreUnknownKeys = true }
        }
    }

    /**
     * Builds the project and evaluates its raw run properties.
     */
    suspend fun getProjectRunProperties(projectPath: Path): Result<RunProperties> = catchExecutionErrors {
        val dotNetCliPath = getDotNetCliPath()
        buildProject(projectPath, dotNetCliPath)
        evaluateRunProperties(projectPath, dotNetCliPath)
    }

    /**
     * Evaluates the raw run properties of a project that was already built externally, using the same build inputs.
     * The project isn't built.
     */
    suspend fun getExternallyBuiltProjectRunProperties(
        projectPath: Path,
        configuration: String?,
        environment: Map<String, String>,
        workingDirectory: Path?
    ): Result<RunProperties> = catchExecutionErrors {
        val dotNetCliPath = getDotNetCliPath()
        evaluateRunProperties(projectPath, dotNetCliPath, configuration, environment, workingDirectory)
    }

    /**
     * Builds the project and evaluates its target framework.
     */
    suspend fun getProjectTargetFramework(projectPath: Path): Result<RdTargetFrameworkId> = catchExecutionErrors {
        val dotNetCliPath = getDotNetCliPath()
        buildProject(projectPath, dotNetCliPath)

        val properties = evaluateRunProperties(projectPath, dotNetCliPath)

        parseTargetFrameworkId(properties.targetFramework)
            ?: throw ExecutionException("Unable to parse target framework '${properties.targetFramework}' of $projectPath")
    }

    private suspend fun buildProject(projectPath: Path, dotNetCliPath: Path) {
        val commandLine = GeneralCommandLine()
            .withExePath(dotNetCliPath.absolutePathString())
            .withParameters("build", projectPath.absolutePathString())

        runDotNetCli(
            commandLine,
            "Unable to build project $projectPath",
            "Building ${projectPath.nameWithoutExtension}"
        )
    }

    private suspend fun evaluateRunProperties(
        projectPath: Path,
        dotNetCliPath: Path,
        configuration: String? = null,
        environment: Map<String, String> = emptyMap(),
        workingDirectory: Path? = null
    ): RunProperties = withContext(Dispatchers.IO) {
        val temporaryDirectory = Files.createTempDirectory("aspire-msbuild-")
        try {
            val resultFile = temporaryDirectory.resolve("run-properties.json")
            val isFileBasedProgram = projectPath.extension.equals("cs", true)
            val commandLine = GeneralCommandLine()
                .withExePath(dotNetCliPath.absolutePathString())
                .withWorkingDirectory(workingDirectory ?: projectPath.parent)
                .withEnvironment(environment)
                .withParameters(if (isFileBasedProgram) "build" else "msbuild", projectPath.absolutePathString())
            if (isFileBasedProgram) {
                // The file-based program is already restored by the previous build
                commandLine.addParameter("--no-restore")
            }
            if (!configuration.isNullOrEmpty()) {
                commandLine.addParameter("-property:Configuration=${escapeMSBuildProperty(configuration)}")
            }
            commandLine.addParameters(
                "-target:ComputeRunArguments",
                "-getProperty:$RUN_PROPERTIES",
                "-getResultOutputFile:$resultFile",
                "-nologo",
                "-v:q"
            )
            if (environment.isNotEmpty()) {
                // The external build receives these values as global properties.
                // A response file keeps them out of the process command line.
                val responseFile = temporaryDirectory.resolve("build-properties.rsp")
                Files.write(responseFile, environment.map { (name, value) ->
                    "\"-property:${escapeMSBuildProperty(name)}=${escapeMSBuildProperty(value)}\""
                })
                commandLine.addParameter("@$responseFile")
            }

            runDotNetCli(
                commandLine,
                "Unable to evaluate run properties of $projectPath",
                "Evaluating ${projectPath.nameWithoutExtension}"
            )

            decodeRunProperties(Files.readString(resultFile), projectPath)
        } finally {
            NioFiles.deleteRecursively(temporaryDirectory)
        }
    }

    private fun getDotNetCliPath(): Path = getActiveDotNetCliPath(project).getOrThrow()

    private suspend fun runDotNetCli(commandLine: GeneralCommandLine, failureMessage: String, progressTitle: String) {
        val output = withContext(Dispatchers.IO) {
            withBackgroundProgress(project, progressTitle) {
                ExecUtil.execAndGetOutput(commandLine)
            }
        }
        if (output.exitCode != 0) {
            val details = output.stderr.ifBlank { output.stdout }.trim()
            throw ExecutionException("$failureMessage (exit code ${output.exitCode})\n$details")
        }
    }

    private fun decodeRunProperties(text: String, projectPath: Path): RunProperties =
        try {
            json.decodeFromString<ProjectRunPropertiesOutput>(text).properties
        } catch (e: SerializationException) {
            throw ExecutionException("Unable to read run properties of $projectPath", e)
        } catch (e: IllegalArgumentException) {
            throw ExecutionException("Unable to read run properties of $projectPath", e)
        }

    // See: https://learn.microsoft.com/visualstudio/msbuild/how-to-escape-special-characters-in-msbuild
    private fun escapeMSBuildProperty(value: String) = buildString {
        for (character in value) {
            if (character in "%$@()*\\\"',;?=\t\r\n ") {
                append('%')
                append(character.code.toString(16).uppercase().padStart(2, '0'))
            } else {
                append(character)
            }
        }
    }

    // Only expected failures become a Result; cancellation and programming errors propagate
    private inline fun <T> catchExecutionErrors(block: () -> T): Result<T> =
        try {
            Result.success(block())
        } catch (e: ExecutionException) {
            Result.failure(e)
        }
}
