package com.jetbrains.rider.aspire.run

import com.intellij.execution.actions.ConfigurationContext
import com.intellij.execution.actions.LazyRunConfigurationProducer
import com.intellij.execution.configurations.ConfigurationTypeUtil
import com.intellij.openapi.util.Ref
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.util.io.systemIndependentPath
import com.intellij.psi.PsiElement
import com.jetbrains.rd.util.firstOrNull
import com.jetbrains.rider.aspire.launchProfiles.getApplicationUrl
import com.jetbrains.rider.aspire.launchProfiles.getArguments
import com.jetbrains.rider.aspire.launchProfiles.getEnvironmentVariables
import com.jetbrains.rider.aspire.launchProfiles.getWorkingDirectory
import com.jetbrains.rider.model.runnableProjectsModel
import com.jetbrains.rider.projectView.solution
import com.jetbrains.rider.projectView.workspace.getFile
import com.jetbrains.rider.run.configurations.getSelectedProject
import com.jetbrains.rider.run.configurations.launchSettings.LaunchSettingsJsonService

class AspireHostRunConfigurationProducer : LazyRunConfigurationProducer<AspireHostConfiguration>() {
    override fun getConfigurationFactory() =
        ConfigurationTypeUtil
            .findConfigurationType(AspireHostConfigurationType::class.java)
            .factory

    override fun isConfigurationFromContext(
        configuration: AspireHostConfiguration,
        context: ConfigurationContext
    ): Boolean {
        val selectedProjectFilePath = context.getSelectedProject()?.getFile()?.systemIndependentPath ?: return false

        val projects = context.project.solution.runnableProjectsModel.projects.valueOrNull ?: return false
        val configurationProjectFilePath = FileUtil.toSystemIndependentName(configuration.parameters.projectFilePath)
        val runnableProject = projects.firstOrNull {
            it.kind == AspireRunnableProjectKinds.AspireHost &&
                    FileUtil.toSystemIndependentName(it.projectFilePath) == selectedProjectFilePath &&
                    configurationProjectFilePath == selectedProjectFilePath
        }

        return runnableProject != null
    }

    override fun setupConfigurationFromContext(
        configuration: AspireHostConfiguration,
        context: ConfigurationContext,
        sourceElement: Ref<PsiElement?>
    ): Boolean {
        val selectedProjectFilePath = context.getSelectedProject()?.getFile()?.systemIndependentPath
            ?: return false

        val runnableProjects = context.project.solution.runnableProjectsModel.projects.valueOrNull ?: return false
        val runnableProject = runnableProjects.firstOrNull {
            it.kind == AspireRunnableProjectKinds.AspireHost &&
                    FileUtil.toSystemIndependentName(it.projectFilePath) == selectedProjectFilePath
        } ?: return false

        if (configuration.name.isEmpty()) {
            configuration.name = runnableProject.name
        }

        val projectOutput = runnableProject
            .projectOutputs
            .firstOrNull()
        val profile = LaunchSettingsJsonService
            .getInstance(context.project)
            .loadLaunchSettings(runnableProject)
            ?.profiles
            ?.firstOrNull()

        configuration.parameters.apply {
            projectFilePath = selectedProjectFilePath
            projectTfm = projectOutput?.tfm?.presentableName ?: ""
            profileName = profile?.key ?: ""
            trackArguments = true
            arguments = getArguments(profile?.value, projectOutput)
            trackWorkingDirectory = true
            workingDirectory = getWorkingDirectory(profile?.value, projectOutput)
            trackEnvs = true
            envs = getEnvironmentVariables(profile?.key, profile?.value)
            usePodmanRuntime = false
            trackUrl = true
            startBrowserParameters.apply {
                url = getApplicationUrl(profile?.value)
                startAfterLaunch = profile?.value?.launchBrowser == true
            }
        }

        return true
    }
}