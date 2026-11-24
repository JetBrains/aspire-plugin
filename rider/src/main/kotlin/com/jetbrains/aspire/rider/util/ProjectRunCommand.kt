package com.jetbrains.aspire.rider.util

import com.intellij.execution.ExecutionException
import com.intellij.execution.configurations.PathEnvironmentVariableUtil
import com.intellij.util.execution.ParametersListUtil
import com.jetbrains.rider.model.RdTargetFrameworkId
import org.jetbrains.annotations.ApiStatus
import java.nio.file.Path
import kotlin.io.path.Path
import kotlin.io.path.isRegularFile

/**
 * The command that MSBuild `ComputeRunArguments` resolves to run a project.
 *
 * @property runsProjectOutput `true` if the command runs the project output (`TargetPath` or its apphost),
 * so the process can be debugged.
 */
@ApiStatus.Internal
data class ProjectRunCommand(
    val executablePath: Path,
    val arguments: List<String>,
    val workingDirectory: Path,
    val runsProjectOutput: Boolean,
)

/**
 * Run properties of a project resolved from its raw MSBuild [RunProperties].
 *
 * @property targetFramework the target framework of the project.
 * @property runCommand the command that runs the project.
 */
@ApiStatus.Internal
data class ProjectRunProperties(
    val targetFramework: RdTargetFrameworkId,
    val runCommand: ProjectRunCommand
)

/**
 * Resolves the target framework and the command that runs the project from its raw run properties.
 *
 * @param dotNetCliPath the .NET CLI used to evaluate the properties; it runs the SDK-provided `dotnet` commands.
 */
internal fun RunProperties.toProjectRunProperties(
    projectPath: Path,
    dotNetCliPath: Path
): Result<ProjectRunProperties> {
    val targetFrameworkId = parseTargetFrameworkId(targetFramework)
        ?: return Result.failure(ExecutionException("Unable to parse target framework '$targetFramework' of $projectPath"))
    val runCommand = resolveProjectRunCommand(projectPath, this, dotNetCliPath)
        .getOrElse { return Result.failure(it) }
    if (runCommand.runsProjectOutput && !runCommand.executablePath.isRegularFile()) {
        return Result.failure(ExecutionException("Unable to find the output of $projectPath at ${runCommand.executablePath}"))
    }

    return Result.success(ProjectRunProperties(targetFrameworkId, runCommand))
}

/**
 * @param dotNetCliPath the .NET CLI used to evaluate the [properties]; it runs the SDK-provided `dotnet` commands.
 */
@ApiStatus.Internal
fun resolveProjectRunCommand(
    projectPath: Path,
    properties: RunProperties,
    dotNetCliPath: Path
): Result<ProjectRunCommand> {
    val runCommand = properties.runCommand.trim().trim('"')
    if (runCommand.isEmpty()) {
        return Result.failure(ExecutionException("MSBuild RunCommand of $projectPath is empty"))
    }

    // ComputeRunArguments extensions can return relative paths, MSBuild resolves them against the project directory
    val projectDirectory = projectPath.parent
    val workingDirectory = projectDirectory.resolve(properties.runWorkingDirectory).normalize()
    val targetPath = properties.targetPath.takeIf { it.isNotBlank() }?.let { projectDirectory.resolve(it).normalize() }
    val arguments = ParametersListUtil.parse(properties.runArguments)

    if (isDotNetHost(runCommand)) {
        // The SDK runs framework-dependent output as `dotnet exec <TargetPath>`.
        // Run the assembly directly so that Rider uses its .NET runtime and can debug it.
        val assemblyPath = arguments.getOrNull(1)?.let { workingDirectory.resolve(it).normalize() }
        if (targetPath != null && arguments.firstOrNull() == "exec" && assemblyPath == targetPath) {
            return Result.success(ProjectRunCommand(targetPath, arguments.drop(2), workingDirectory, true))
        }

        // Other `dotnet` commands (e.g. `dotnet exec` of a dev server) run with the same .NET CLI as MSBuild
        return Result.success(ProjectRunCommand(dotNetCliPath, arguments, workingDirectory, false))
    }

    // The command runs either the assembly itself or its apphost next to it
    val executablePath = resolveExecutablePath(runCommand, workingDirectory)
    val runsProjectOutput =
        targetPath != null && executablePath.pathWithoutExeOrDllExtension == targetPath.pathWithoutExeOrDllExtension

    return Result.success(ProjectRunCommand(executablePath, arguments, workingDirectory, runsProjectOutput))
}

// A full path to `dotnet` is a user program, not the SDK-provided host
private fun isDotNetHost(command: String) =
    command.equals("dotnet", true) || command.equals("dotnet.exe", true)

@Suppress("UnstableApiUsage")
private fun resolveExecutablePath(command: String, workingDirectory: Path): Path {
    val commandPath = Path(command)
    if (commandPath.parent == null) {
        PathEnvironmentVariableUtil.findFirst(command)?.let { return it }
    }

    return workingDirectory.resolve(commandPath).normalize()
}
