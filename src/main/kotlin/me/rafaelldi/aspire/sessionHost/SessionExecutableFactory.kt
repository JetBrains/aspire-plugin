package me.rafaelldi.aspire.sessionHost

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.util.execution.ParametersListUtil
import com.intellij.util.io.systemIndependentPath
import com.jetbrains.rider.model.RunnableProject
import com.jetbrains.rider.model.runnableProjectsModel
import com.jetbrains.rider.projectView.solution
import com.jetbrains.rider.run.configurations.RunnableProjectKinds
import com.jetbrains.rider.runtime.DotNetExecutable
import com.jetbrains.rider.runtime.dotNetCore.DotNetCoreRuntimeType
import me.rafaelldi.aspire.generated.SessionModel
import me.rafaelldi.aspire.settings.AspireSettings
import kotlin.io.path.Path

@Service(Service.Level.PROJECT)
class SessionExecutableFactory(private val project: Project) {
    companion object {
        fun getInstance(project: Project) = project.service<SessionExecutableFactory>()

        private const val OTEL_EXPORTER_OTLP_ENDPOINT = "OTEL_EXPORTER_OTLP_ENDPOINT"
        private fun getOtlpEndpoint(port: Int) = "http://localhost:$port"
    }

    fun createExecutable(sessionModel: SessionModel, openTelemetryPort: Int): DotNetExecutable? {
        val runnableProjects = project.solution.runnableProjectsModel.projects.valueOrNull
        val sessionProjectPath = Path(sessionModel.projectPath)
        val sessionProjectPathString = sessionProjectPath.systemIndependentPath
        val runnableProject = runnableProjects?.singleOrNull {
            it.projectFilePath == sessionProjectPathString && it.kind == RunnableProjectKinds.DotNetCore
        }

        return if (runnableProject != null) {
            getExecutableForRunnableProject(runnableProject, sessionModel, openTelemetryPort)
        } else {
            null
        }
    }

    private fun getExecutableForRunnableProject(
        runnableProject: RunnableProject,
        sessionModel: SessionModel,
        openTelemetryPort: Int
    ): DotNetExecutable? {
        val output = runnableProject.projectOutputs.firstOrNull() ?: return null
        val executablePath = output.exePath
        val workingDirectory = output.workingDirectory
        val arguments =
            if (sessionModel.args?.isNotEmpty() == true) sessionModel.args.toList()
            else output.defaultArguments
        val params = ParametersListUtil.join(arguments)
        val envs = sessionModel.envs?.associate { it.key to it.value }?.toMutableMap() ?: mutableMapOf()
        if (AspireSettings.getInstance().collectTelemetry) {
            envs.put(OTEL_EXPORTER_OTLP_ENDPOINT, getOtlpEndpoint(openTelemetryPort))
        }

        return DotNetExecutable(
            executablePath,
            output.tfm,
            workingDirectory,
            params,
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
}