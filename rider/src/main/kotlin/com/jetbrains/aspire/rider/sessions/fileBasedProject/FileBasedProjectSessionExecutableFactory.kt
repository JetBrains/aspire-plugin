package com.jetbrains.aspire.rider.sessions.fileBasedProject

import com.intellij.ide.browsers.StartBrowserSettings
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.diagnostic.trace
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.io.toNioPathOrNull
import com.jetbrains.aspire.rider.run.AspireRunConfiguration
import com.jetbrains.aspire.rider.sessions.getLaunchProfile
import com.jetbrains.aspire.rider.sessions.getStartBrowserSettings
import com.jetbrains.aspire.rider.sessions.mergeArguments
import com.jetbrains.aspire.rider.sessions.mergeEnvironmentVariables
import com.jetbrains.aspire.rider.util.getStartBrowserAction
import com.jetbrains.aspire.sessions.DotNetSessionLaunchConfiguration
import com.jetbrains.aspire.settings.AspireSettings
import com.jetbrains.rider.model.runnableProjectsModel
import com.jetbrains.rider.projectView.solution
import com.jetbrains.rider.run.configurations.TerminalMode
import com.jetbrains.rider.run.environment.ExecutableParameterProcessor
import com.jetbrains.rider.run.environment.ExecutableRunParameters
import com.jetbrains.rider.run.environment.ProjectProcessOptions
import com.jetbrains.rider.runtime.DotNetExecutable
import com.jetbrains.rider.runtime.dotNetCore.DotNetCoreRuntimeType
import java.nio.file.Path
import kotlin.io.path.Path
import kotlin.io.path.absolutePathString

/**
 * Factory class for creating instances of [DotNetExecutable] from an Aspire session request
 * based on a file-based (single `.cs` file) .NET program.
 *
 * The file-based program is first loaded into a file-based project (see
 * [com.jetbrains.rider.run.configurations.dotNetFile.FileBasedProgramProjectManager]); this factory
 * relies on that generated project (identified by [fileBasedProjectPath]) to obtain the project output.
 */
@Service(Service.Level.PROJECT)
internal class FileBasedProjectSessionExecutableFactory(private val project: Project) {
    companion object {
        fun getInstance(project: Project): FileBasedProjectSessionExecutableFactory = project.service()
        private val LOG = logger<FileBasedProjectSessionExecutableFactory>()
    }

    suspend fun createExecutable(
        launchConfiguration: DotNetSessionLaunchConfiguration,
        fileBasedProjectPath: Path,
        aspireRunConfiguration: AspireRunConfiguration?,
        addBrowserAction: Boolean
    ): Pair<DotNetExecutable, StartBrowserSettings?>?  {
        val sessionProjectPath = launchConfiguration.projectPath

        val runnableProject = project.solution.runnableProjectsModel.projects.valueOrNull?.singleOrNull {
            it.projectFilePath.toNioPathOrNull() == fileBasedProjectPath
        }
        if (runnableProject == null) {
            LOG.warn("Unable to find a runnable project for the file based project $fileBasedProjectPath")
            return null
        }

        val output = runnableProject.projectOutputs.firstOrNull()
        if (output == null) {
            LOG.warn("Unable to find a project output for the file based project $fileBasedProjectPath")
            return null
        }

        val launchProfile = getLaunchProfile(launchConfiguration, runnableProject, project)

        val executablePath = output.exePath
        // `dotnet run --file` uses the `.cs` file parent directory as the default working directory.
        val defaultWorkingDirectory = sessionProjectPath.parent?.absolutePathString() ?: ""
        val workingDirectory = launchProfile?.workingDirectory ?: defaultWorkingDirectory
        val arguments = mergeArguments(launchConfiguration.args, output.defaultArguments, launchProfile?.commandLineArgs)
        val envs = mergeEnvironmentVariables(launchConfiguration.envs, launchProfile?.environmentVariables)

        val processOptions = ProjectProcessOptions(
            fileBasedProjectPath,
            sessionProjectPath.parent
        )
        val runParameters = ExecutableRunParameters(
            executablePath,
            workingDirectory,
            arguments,
            envs,
            true,
            output.tfm
        )

        val executableParams = ExecutableParameterProcessor
            .getInstance(project)
            .processEnvironment(runParameters, processOptions)

        val browserSettings = launchProfile?.let {
            getStartBrowserSettings(project, Path(runnableProject.projectFilePath), it, envs, output.tfm, aspireRunConfiguration)
        }
        val launchBrowser = AspireSettings.getInstance().doNotLaunchBrowserForProjects.not()
        val browserAction =
            if (launchBrowser && addBrowserAction && browserSettings != null && aspireRunConfiguration != null) {
                getStartBrowserAction(aspireRunConfiguration, browserSettings)
            } else {
                { _, _, _ -> }
            }

        LOG.trace { "Executable parameters for file based project ($sessionProjectPath): $executableParams" }
        LOG.trace { "Browser settings for external project (${sessionProjectPath.absolutePathString()}): $browserSettings" }

        return DotNetExecutable(
            executableParams.executablePath ?: executablePath,
            executableParams.tfm ?: output.tfm,
            executableParams.workingDirectoryPath ?: workingDirectory,
            executableParams.commandLineArgumentString ?: arguments,
            TerminalMode.Auto,
            executableParams.environmentVariables,
            false,
            browserAction,
            null,
            "",
            !executablePath.endsWith(".dll", true),
            DotNetCoreRuntimeType
        ) to browserSettings
    }

}
