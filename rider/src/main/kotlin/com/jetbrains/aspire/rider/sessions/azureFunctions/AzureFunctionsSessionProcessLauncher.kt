package com.jetbrains.aspire.rider.sessions.azureFunctions

import com.intellij.execution.process.ProcessListener
import com.intellij.ide.browsers.StartBrowserSettings
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.jetbrains.aspire.rider.run.AspireRunConfiguration
import com.jetbrains.aspire.rider.sessions.findBySessionProject
import com.jetbrains.aspire.rider.sessions.projectLaunchers.DotNetSessionProcessLauncher
import com.jetbrains.aspire.sessions.DotNetSessionLaunchConfiguration
import com.jetbrains.rd.util.lifetime.Lifetime
import com.jetbrains.rider.model.runnableProjectsModel
import com.jetbrains.rider.projectView.solution
import com.jetbrains.rider.run.configurations.RunnableProjectKinds
import com.jetbrains.rider.runtime.DotNetExecutable
import com.jetbrains.rider.runtime.dotNetCore.DotNetCoreRuntime
import java.nio.file.Path

/**
 * Launches an Azure Function .NET project from an Aspire session request.
 */
internal class AzureFunctionsSessionProcessLauncher : DotNetSessionProcessLauncher() {
    companion object {
        private val LOG = logger<AzureFunctionsSessionProcessLauncher>()
    }

    override val priority = 3

    override suspend fun isApplicable(
        projectPath: Path,
        project: Project
    ): Boolean {
        val runnableProject = project.solution.runnableProjectsModel.findBySessionProject(projectPath) {
            it.kind == RunnableProjectKinds.AzureFunctions
        }

        return runnableProject != null
    }

    override suspend fun getDotNetExecutable(
        launchConfiguration: DotNetSessionLaunchConfiguration,
        isDebugSession: Boolean,
        aspireRunConfiguration: AspireRunConfiguration?,
        project: Project,
        sessionProcessLifetime: Lifetime
    ): Pair<DotNetExecutable, StartBrowserSettings?>? {
        val factory = AzureFunctionsSessionExecutableFactory.getInstance(project)
        val executable = factory.createExecutable(launchConfiguration, aspireRunConfiguration)
        if (executable == null) {
            LOG.warn("Unable to create executable for project: ${launchConfiguration.projectPath}")
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
    ) = AzureFunctionsSessionRunProfile(
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
    ) = AzureFunctionsSessionDebugProfile(
        sessionId,
        projectPath,
        dotnetExecutable,
        dotnetRuntime,
        sessionProcessEventListener,
        sessionProcessLifetime,
        aspireHostProjectPath
    )
}