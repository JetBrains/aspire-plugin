package com.jetbrains.aspire.rider.sessions.fileBasedProject

import com.intellij.execution.process.ProcessListener
import com.intellij.ide.browsers.StartBrowserSettings
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.diagnostic.trace
import com.intellij.openapi.project.Project
import com.jetbrains.aspire.rider.run.AspireRunConfiguration
import com.jetbrains.aspire.rider.sessions.dotnetProject.DotNetProjectHotReloadConfigurationExtension
import com.jetbrains.aspire.rider.sessions.projectLaunchers.DotNetSessionWithHotReloadProcessLauncher
import com.jetbrains.aspire.sessions.DotNetSessionLaunchConfiguration
import com.jetbrains.rd.ide.model.RdFileBasedProgramSource
import com.jetbrains.rd.util.lifetime.Lifetime
import com.jetbrains.rider.ijent.extensions.toRd
import com.jetbrains.rider.run.configurations.dotNetFile.FileBasedProgramProjectManager
import com.jetbrains.rider.runtime.DotNetExecutable
import com.jetbrains.rider.runtime.dotNetCore.DotNetCoreRuntime
import java.nio.file.Path
import kotlin.io.path.extension

/**
 * Launches a file-based (single `.cs` file) .NET program from an Aspire session request.
 *
 * A file-based program is not backed by a `.csproj`, so before an executable can be created the program has to
 * be loaded into a file-based project. This is done in [launchRunProcess] and [launchDebugProcess] where the
 * resulting project (bound to the session lifetime) is remembered and later used by [getDotNetExecutable].
 */
internal class FileBasedProjectSessionProcessLauncher : DotNetSessionWithHotReloadProcessLauncher() {
    companion object {
        private val LOG = logger<FileBasedProjectSessionProcessLauncher>()
    }

    override val priority = 3

    override val hotReloadExtension = DotNetProjectHotReloadConfigurationExtension()

    override suspend fun isApplicable(projectPath: Path, project: Project): Boolean =
        projectPath.extension.equals("cs", ignoreCase = true)

    @Suppress("UnstableApiUsage")
    private suspend fun loadFileBasedProject(
        csFilePath: Path,
        sessionProcessLifetime: Lifetime,
        project: Project
    ): Path? {
        LOG.trace { "Loading file based project for $csFilePath" }

        val sourceFile = RdFileBasedProgramSource(csFilePath.toRd())
        val projectManager = FileBasedProgramProjectManager.getInstance(project)
        val fileBasedProjectPath = projectManager.createProjectFile(sourceFile, sessionProcessLifetime)
            ?: return null

        return fileBasedProjectPath
    }

    override suspend fun getDotNetExecutable(
        launchConfiguration: DotNetSessionLaunchConfiguration,
        isDebugSession: Boolean,
        aspireRunConfiguration: AspireRunConfiguration?,
        project: Project,
        sessionProcessLifetime: Lifetime
    ): Pair<DotNetExecutable, StartBrowserSettings?>? {
        val csFilePath = launchConfiguration.projectPath
        if(!csFilePath.extension.equals("cs", ignoreCase = true)) {
            LOG.warn("Unable to create executable for file based project. File extension should be '.cs': ${launchConfiguration.projectPath}")
            return null
        }

        val fileBasedProjectPath = loadFileBasedProject(csFilePath, sessionProcessLifetime, project)

        if (fileBasedProjectPath == null) {
            LOG.warn("File based project was not loaded for: ${launchConfiguration.projectPath}")
            return null
        }

        val factory = FileBasedProjectSessionExecutableFactory.getInstance(project)
        val executableAndBrowserSettings = factory.createExecutable(launchConfiguration, fileBasedProjectPath, aspireRunConfiguration, true)
        if (executableAndBrowserSettings == null) {
            LOG.warn("Unable to create executable for file based project: ${launchConfiguration.projectPath}")
            return null
        }

        return executableAndBrowserSettings
    }

    override fun getRunProfile(
        sessionId: String,
        projectPath: Path,
        dotnetExecutable: DotNetExecutable,
        dotnetRuntime: DotNetCoreRuntime,
        sessionProcessEventListener: ProcessListener,
        sessionProcessLifetime: Lifetime,
        aspireHostProjectPath: Path?
    ) = FileBasedProjectSessionRunProfile(
        sessionId,
        projectPath,
        dotnetExecutable,
        dotnetRuntime,
        sessionProcessEventListener,
        sessionProcessLifetime,
        aspireHostProjectPath
    )

    override fun getDebugProfile(
        sessionId: String,
        projectPath: Path,
        dotnetExecutable: DotNetExecutable,
        dotnetRuntime: DotNetCoreRuntime,
        browserSettings: StartBrowserSettings?,
        sessionProcessEventListener: ProcessListener,
        sessionProcessLifetime: Lifetime,
        aspireHostProjectPath: Path?
    ) = FileBasedProjectSessionDebugProfile(
        sessionId,
        projectPath,
        dotnetExecutable,
        dotnetRuntime,
        sessionProcessEventListener,
        sessionProcessLifetime,
        aspireHostProjectPath
    )
}