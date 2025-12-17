package com.jetbrains.aspire.rider.sessions.executableLibrary

import com.intellij.execution.process.ProcessListener
import com.intellij.ide.browsers.StartBrowserSettings
import com.intellij.openapi.application.EDT
import com.intellij.openapi.components.serviceAsync
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.diagnostic.trace
import com.intellij.openapi.project.Project
import com.intellij.platform.backend.workspace.WorkspaceModel
import com.jetbrains.rd.util.lifetime.Lifetime
import com.jetbrains.aspire.generated.aspirePluginModel
import com.jetbrains.aspire.run.host.AspireHostConfiguration
import com.jetbrains.aspire.rider.sessions.projectLaunchers.DotNetSessionProcessLauncher
import com.jetbrains.aspire.sessions.DotNetSessionLaunchConfiguration
import com.jetbrains.rd.util.lifetime.Lifetime
import com.jetbrains.rider.ijent.extensions.toRd
import com.jetbrains.rider.model.RdProjectDescriptor
import com.jetbrains.rider.projectView.solution
import com.jetbrains.rider.projectView.workspace.getProjectModelEntities
import com.jetbrains.rider.projectView.workspace.isProject
import com.jetbrains.rider.runtime.DotNetExecutable
import com.jetbrains.rider.runtime.dotNetCore.DotNetCoreRuntime
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.nio.file.Path
import kotlin.io.path.Path

/**
 * Launches a .NET library project that has an `Executable` launch profile from an Aspire session request.
 */
internal class ExecutableLibrarySessionProcessLauncher : DotNetSessionProcessLauncher() {
    companion object {
        private val LOG = logger<ExecutableLibrarySessionProcessLauncher>()

        private const val LIBRARY = "library"
    }

    override val priority = 3

    override suspend fun isApplicable(
        projectPath: String,
        project: Project
    ): Boolean {
        val path = Path(projectPath)
        val entity = project.serviceAsync<WorkspaceModel>()
            .getProjectModelEntities(path, project)
            .singleOrNull { it.isProject() }
        if (entity == null) {
            LOG.trace { "Can't find a project entity for the path $projectPath. Skip launcher" }
            return false
        }

        val descriptor = entity.descriptor as? RdProjectDescriptor
        if (descriptor == null) {
            LOG.trace { "Can't find an RdProjectDescriptor for the path $projectPath. Skip launcher" }
            return false
        }

        val outputType = withContext(Dispatchers.EDT) {
            project.solution.aspirePluginModel.getProjectOutputType.startSuspending(projectPath.toRd())
        }

        return outputType?.equals(LIBRARY, true) == true
    }

    override suspend fun getDotNetExecutable(
        launchConfiguration: DotNetSessionLaunchConfiguration,
        isDebugSession: Boolean,
        hostRunConfiguration: AspireHostConfiguration?,
        project: Project
    ): Pair<DotNetExecutable, StartBrowserSettings?>? {
        val factory = ExecutableLibraryExecutableFactory.getInstance(project)
        val executable = factory.createExecutable(launchConfiguration)
        if (executable == null) {
            LOG.warn("Unable to create library executable for project: ${launchConfiguration.projectPath}")
            return null
        }

        return executable to null
    }

    override fun getRunProfile(
        sessionId: String,
        projectPath: Path,
        dotnetExecutable: DotNetExecutable,
        dotnetRuntime: DotNetCoreRuntime,
        sessionProcessEventListener: ProcessListener,
        sessionProcessLifetime: Lifetime,
        aspireHostProjectPath: Path?
    ) = ExecutableLibrarySessionRunProfile(
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
    ) = ExecutableLibrarySessionDebugProfile(
        sessionId,
        projectPath,
        dotnetExecutable,
        dotnetRuntime,
        sessionProcessEventListener,
        sessionProcessLifetime,
        aspireHostProjectPath
    )
}