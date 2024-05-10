package me.rafaelldi.aspire.sessionHost

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.util.execution.ParametersListUtil
import com.intellij.util.io.systemIndependentPath
import com.jetbrains.rider.model.RunnableProject
import com.jetbrains.rider.model.runnableProjectsModel
import com.jetbrains.rider.projectView.solution
import com.jetbrains.rider.runtime.DotNetExecutable
import com.jetbrains.rider.runtime.dotNetCore.DotNetCoreRuntimeType
import me.rafaelldi.aspire.generated.SessionModel
import me.rafaelldi.aspire.util.MSBuildPropertyService
import java.nio.file.Path
import kotlin.io.path.Path

@Service(Service.Level.PROJECT)
class SessionExecutableFactory(private val project: Project) {
    companion object {
        fun getInstance(project: Project) = project.service<SessionExecutableFactory>()
    }

    suspend fun createExecutable(sessionModel: SessionModel): DotNetExecutable? {
        val sessionProjectPath = Path(sessionModel.projectPath)
        val runnableProject = project.solution.runnableProjectsModel.findBySessionProject(sessionProjectPath)

        return if (runnableProject != null) {
            getExecutableForRunnableProject(runnableProject, sessionModel)
        } else {
            getExecutableForExternalProject(sessionProjectPath, sessionModel)
        }
    }

    private fun getExecutableForRunnableProject(
        runnableProject: RunnableProject,
        sessionModel: SessionModel
    ): DotNetExecutable? {
        val output = runnableProject.projectOutputs.firstOrNull() ?: return null
        val executablePath = output.exePath
        val arguments =
            if (sessionModel.args?.isNotEmpty() == true) sessionModel.args.toList()
            else output.defaultArguments
        val params = ParametersListUtil.join(arguments)
        val envs = sessionModel.envs?.associate { it.key to it.value } ?: mapOf()

        return DotNetExecutable(
            executablePath,
            output.tfm,
            output.workingDirectory,
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

    private suspend fun getExecutableForExternalProject(
        sessionProjectPath: Path,
        sessionModel: SessionModel
    ): DotNetExecutable? {
        val propertyService = MSBuildPropertyService.getInstance(project)
        val properties = propertyService.getProjectRunProperties(sessionProjectPath) ?: return null
        val executablePath = properties.executablePath.systemIndependentPath
        val arguments =
            if (sessionModel.args?.isNotEmpty() == true) sessionModel.args.toList()
            else properties.arguments
        val params = ParametersListUtil.join(arguments)
        val envs = sessionModel.envs?.associate { it.key to it.value } ?: mapOf()

        return DotNetExecutable(
            executablePath,
            properties.targetFramework,
            properties.workingDirectory.systemIndependentPath,
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