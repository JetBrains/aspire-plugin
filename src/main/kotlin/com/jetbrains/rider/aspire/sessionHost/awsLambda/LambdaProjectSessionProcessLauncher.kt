package com.jetbrains.rider.aspire.sessionHost.awsLambda

import com.intellij.execution.process.ProcessListener
import com.intellij.openapi.components.serviceAsync
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.diagnostic.trace
import com.intellij.openapi.project.Project
import com.intellij.platform.backend.workspace.WorkspaceModel
import com.jetbrains.rd.util.lifetime.Lifetime
import com.jetbrains.rider.aspire.generated.CreateSessionRequest
import com.jetbrains.rider.aspire.generated.aspirePluginModel
import com.jetbrains.rider.aspire.sessionHost.projectLaunchers.SessionProcessLauncherExtension
import com.jetbrains.rider.aspire.sessionHost.projectLaunchers.getDotNetRuntime
import com.jetbrains.rider.model.RdProjectDescriptor
import com.jetbrains.rider.projectView.nodes.getUserData
import com.jetbrains.rider.projectView.solution
import com.jetbrains.rider.projectView.workspace.getProjectModelEntities
import com.jetbrains.rider.projectView.workspace.isProject
import com.jetbrains.rider.runtime.DotNetExecutable
import kotlin.io.path.Path

class LambdaProjectSessionProcessLauncher : SessionProcessLauncherExtension {
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

        val outputType = project.solution.aspirePluginModel.getProjectOutputType.startSuspending(projectPath)

        return outputType?.equals(LIBRARY, true) == true
    }

    override suspend fun launchRunProcess(
        sessionId: String,
        sessionModel: CreateSessionRequest,
        sessionProcessEventListener: ProcessListener,
        sessionProcessLifetime: Lifetime,
        aspireHostRunConfigName: String?,
        project: Project
    ) {
        LOG.trace { "Starting run session for ${sessionModel.projectPath}" }

        val executable = getDotNetExecutable(sessionModel, project) ?: return
        val runtime = getDotNetRuntime(executable, project) ?: return
    }

    override suspend fun launchDebugProcess(
        sessionId: String,
        sessionModel: CreateSessionRequest,
        sessionProcessEventListener: ProcessListener,
        sessionProcessLifetime: Lifetime,
        aspireHostRunConfigName: String?,
        project: Project
    ) {
        LOG.trace { "Starting debug session for project ${sessionModel.projectPath}" }
    }

    private suspend fun getDotNetExecutable(
        sessionModel: CreateSessionRequest,
        project: Project
    ): DotNetExecutable? {
        val factory = AWSLambdaExecutableFactory.getInstance(project)
        val executable = factory.createExecutable(sessionModel)
        if (executable == null) {
            LOG.warn("Unable to create AWS Lambda executable for project: ${sessionModel.projectPath}")
        }

        return executable
    }
}