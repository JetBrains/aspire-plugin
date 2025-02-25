package com.jetbrains.rider.aspire.sessionHost.awsLambda

import com.intellij.execution.process.ProcessListener
import com.intellij.ide.browsers.StartBrowserSettings
import com.intellij.openapi.application.EDT
import com.intellij.openapi.components.serviceAsync
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.diagnostic.trace
import com.intellij.openapi.project.Project
import com.intellij.platform.backend.workspace.WorkspaceModel
import com.jetbrains.rd.util.lifetime.Lifetime
import com.jetbrains.rider.aspire.generated.CreateSessionRequest
import com.jetbrains.rider.aspire.generated.aspirePluginModel
import com.jetbrains.rider.aspire.run.AspireHostConfiguration
import com.jetbrains.rider.aspire.sessionHost.dotnetProject.DotNetProjectSessionDebugProfile
import com.jetbrains.rider.aspire.sessionHost.dotnetProject.DotNetProjectSessionRunProfile
import com.jetbrains.rider.aspire.sessionHost.projectLaunchers.DotNetExecutableSessionProcessLauncher
import com.jetbrains.rider.model.RdProjectDescriptor
import com.jetbrains.rider.projectView.nodes.getUserData
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
 * Provides support for the .NET library projects that have a project property `AWSProjectType` equal to `Lambda`.
 */
class LambdaProjectSessionProcessLauncher : DotNetExecutableSessionProcessLauncher() {
    companion object {
        private val LOG = logger<LambdaProjectSessionProcessLauncher>()

        private const val AWS_PROJECT_TYPE = "AWSProjectType"
        private const val LAMBDA = "Lambda"
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
            LOG.trace { "Can't find a project entity for the path $projectPath" }
            return false
        }

        val descriptor = entity.descriptor as? RdProjectDescriptor
        if (descriptor == null) {
            LOG.trace { "Can't find an RdProjectDescriptor for the path $projectPath" }
            return false
        }

        val awsProjectType = descriptor.getUserData(AWS_PROJECT_TYPE)
        if (awsProjectType?.equals(LAMBDA, true) != true) {
            LOG.trace { "Can't find AWS project type for the path $projectPath" }
            return false
        }

        val outputType = withContext(Dispatchers.EDT) {
            project.solution.aspirePluginModel.getProjectOutputType.startSuspending(projectPath)
        }

        return outputType?.equals(LIBRARY, true) == true
    }

    override suspend fun getDotNetExecutable(
        sessionModel: CreateSessionRequest,
        isDebugSession: Boolean,
        hostRunConfiguration: AspireHostConfiguration?,
        project: Project
    ): Pair<DotNetExecutable, StartBrowserSettings?>? {
        val factory = AWSLambdaExecutableFactory.getInstance(project)
        val executable = factory.createExecutable(sessionModel)
        if (executable == null) {
            LOG.warn("Unable to create AWS Lambda executable for project: ${sessionModel.projectPath}")
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
    ) = DotNetProjectSessionRunProfile(
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
    ) = DotNetProjectSessionDebugProfile(
        sessionId,
        projectPath,
        dotnetExecutable,
        dotnetRuntime,
        sessionProcessEventListener,
        sessionProcessLifetime,
        aspireHostProjectPath
    )
}