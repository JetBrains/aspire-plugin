package com.jetbrains.aspire.rider.run.file

import com.intellij.execution.CantRunException
import com.intellij.execution.configurations.RunProfileState
import com.intellij.execution.executors.DefaultDebugExecutor
import com.intellij.execution.executors.DefaultRunExecutor
import com.intellij.execution.process.ProcessEvent
import com.intellij.execution.process.ProcessListener
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.execution.runners.ProgramRunner
import com.intellij.execution.ui.RunContentDescriptor
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.diagnostic.runAndLogException
import com.intellij.openapi.project.Project
import com.intellij.openapi.rd.createLifetime
import com.intellij.openapi.util.io.toNioPathOrNull
import com.intellij.util.execution.ParametersListUtil
import com.jetbrains.aspire.AspireService
import com.jetbrains.aspire.rider.launchProfiles.*
import com.jetbrains.aspire.rider.run.AspireExecutorFactory
import com.jetbrains.aspire.rider.run.AspireRunnableProjectKinds
import com.jetbrains.aspire.rider.run.states.AspireHostDebugProfileState
import com.jetbrains.aspire.rider.run.states.AspireHostRunProfileState
import com.jetbrains.aspire.util.getStartBrowserAction
import com.jetbrains.rd.ide.model.RdFileBasedProgramSource
import com.jetbrains.rd.util.lifetime.Lifetime
import com.jetbrains.rider.ijent.extensions.toRdPath
import com.jetbrains.rider.model.runnableProjectsModel
import com.jetbrains.rider.projectView.solution
import com.jetbrains.rider.run.configurations.TerminalMode
import com.jetbrains.rider.run.configurations.dotNetFile.FileBasedProgramProjectManager
import com.jetbrains.rider.run.configurations.launchSettings.LaunchSettingsJsonService
import com.jetbrains.rider.run.environment.ExecutableParameterProcessor
import com.jetbrains.rider.run.environment.ExecutableRunParameters
import com.jetbrains.rider.run.environment.ProjectProcessOptions
import com.jetbrains.rider.runtime.DotNetExecutable
import com.jetbrains.rider.runtime.RiderDotNetActiveRuntimeHost
import com.jetbrains.rider.runtime.dotNetCore.DotNetCoreRuntime

internal class AspireFileExecutorFactory(
    private val project: Project,
    private val parameters: AspireFileConfigurationParameters
) : AspireExecutorFactory(project, parameters) {
    companion object {
        private val LOG = logger<AspireFileExecutorFactory>()
    }

    override suspend fun create(
        executorId: String,
        environment: ExecutionEnvironment,
        lifetime: Lifetime
    ): RunProfileState {
        val activeRuntime = RiderDotNetActiveRuntimeHost.getInstance(project).dotNetCoreRuntime.value
            ?: throw CantRunException("Unable to find appropriate dotnet runtime")

        val executable = getDotNetExecutable(activeRuntime,getLifetime(project, parameters.filePath, environment))

        return when (executorId) {
            DefaultRunExecutor.EXECUTOR_ID -> AspireHostRunProfileState(executable, activeRuntime, environment)
            DefaultDebugExecutor.EXECUTOR_ID ->  AspireHostDebugProfileState(executable, activeRuntime, environment)
            else -> throw CantRunException("Unable to execute Aspire host with $executorId executor")
        }
    }

    @Suppress("UnstableApiUsage", "DuplicatedCode")
    private suspend fun getDotNetExecutable(
        activeRuntime: DotNetCoreRuntime,
        projectFileLifetime: Lifetime
    ): DotNetExecutable {
        val sourceFile = RdFileBasedProgramSource(parameters.filePath.toRdPath())
        val projectManager = FileBasedProgramProjectManager.getInstance(project)
        val fileBasedProjectPath = projectManager.createProjectFile(sourceFile, projectFileLifetime)

        val runnableProject = project.solution.runnableProjectsModel.projects.valueOrNull?.singleOrNull {
            it.kind == AspireRunnableProjectKinds.AspireHost && it.projectFilePath.toNioPathOrNull() == fileBasedProjectPath
        }

        val projectOutput = runnableProject?.projectOutputs?.singleOrNull()
            ?: throw CantRunException("Unable to load file based project")

        val launchSettingsPath = parameters.filePath.toNioPathOrNull()?.let { getLaunchSettingsPathForCsFile(it) }
            ?: throw CantRunException("Unable to find launch settings file for ${parameters.filePath}")

        val launchProfile = LaunchSettingsJsonService
            .getInstance(project)
            .getProjectLaunchProfileByName(launchSettingsPath, parameters.profileName)
            ?: throw CantRunException("Profile ${parameters.profileName} not found")

        val effectiveArguments =
            if (parameters.trackArguments) getArguments(launchProfile.content, projectOutput)
            else parameters.arguments

        val defaultWorkingDirectory = parameters.filePath.toNioPathOrNull()?.parent

        val effectiveWorkingDirectory =
            if (parameters.trackWorkingDirectory) launchProfile.content.workingDirectory ?: defaultWorkingDirectory.toString()
            else parameters.workingDirectory

        val effectiveEnvs =
            if (parameters.trackEnvs) getEnvironmentVariables(launchProfile.name, launchProfile.content).toMutableMap()
            else parameters.envs.toMutableMap() // TODO: Apply TerminalMode
        val environmentVariableValues = configureEnvironmentVariables(effectiveEnvs, activeRuntime)

        var effectiveUrl =
            if (parameters.trackUrl) getApplicationUrl(launchProfile.content)
            else parameters.startBrowserParameters.url
        if (parameters.trackUrl && environmentVariableValues.browserToken != null) {
            effectiveUrl = configureUrl(effectiveUrl, environmentVariableValues.browserToken)
        }

        val effectiveLaunchBrowser =
            if (parameters.trackBrowserLaunch) getLaunchBrowserFlag(launchProfile.content)
            else parameters.startBrowserParameters.startAfterLaunch

        val processOptions = ProjectProcessOptions(
            fileBasedProjectPath,
            defaultWorkingDirectory
        )

        val runParameters = ExecutableRunParameters(
            projectOutput.exePath,
            effectiveWorkingDirectory,
            effectiveArguments,
            effectiveEnvs,
            true,
            projectOutput.tfm
        )

        val params = ExecutableParameterProcessor
            .getInstance(project)
            .processEnvironment(runParameters, processOptions)

        return DotNetExecutable(
            params.executablePath ?: projectOutput.exePath,
            params.tfm ?: projectOutput.tfm,
            params.workingDirectoryPath ?: defaultWorkingDirectory?.toString() ?: "",
            params.commandLineArgumentString ?: ParametersListUtil.join(projectOutput.defaultArguments),
            TerminalMode.Auto,
            params.environmentVariables,
            true,
            getStartBrowserAction(effectiveUrl, effectiveLaunchBrowser, parameters.startBrowserParameters),
            null,
            "",
            true
        )
    }

    @Suppress("UnstableApiUsage")
    private fun getLifetime(project: Project, sourceFile: String, environment: ExecutionEnvironment): Lifetime {
        val ld = AspireService.getInstance(project).lifetime.createNested()
        val oldCallback = environment.callback
        LOG.trace("Initializing lifetime for run configuration of file \"${sourceFile}\".")

        environment.callback = object : ProgramRunner.Callback {
            override fun processStarted(descriptor: RunContentDescriptor?) {
                LOG.runAndLogException { oldCallback?.processStarted(descriptor) }

                LOG.trace("Process for run configuration of file \"${sourceFile}\" started.")
                if (descriptor == null) {
                    LOG.error("No content descriptor found. Lifetime won't be terminated until solution close.")
                } else {
                    val processHandler = descriptor.processHandler
                    if (processHandler == null) {
                        LOG.error("Cannot get process handler for a descriptor: will terminate only with the descriptor.")
                        descriptor.createLifetime().onTermination {
                            LOG.trace("Descriptor lifetime terminated.")
                            ld.terminate()
                        }
                    } else {
                        processHandler.addProcessListener(object : ProcessListener {
                            override fun processTerminated(event: ProcessEvent) {
                                LOG.trace("Process lifetime terminated.")
                                ld.terminate()
                            }
                        })
                    }
                }
            }

            override fun processNotStarted(error: Throwable?) {
                LOG.runAndLogException { oldCallback?.processNotStarted(error) }

                LOG.trace("Process for run configuration of file \"${sourceFile}\" not started.")
                ld.terminate()
            }
        }

        return ld.lifetime
    }
}