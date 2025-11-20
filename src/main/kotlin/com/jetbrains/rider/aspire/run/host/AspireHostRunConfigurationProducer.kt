package com.jetbrains.rider.aspire.run.host

import com.intellij.execution.actions.ConfigurationContext
import com.intellij.execution.actions.LazyRunConfigurationProducer
import com.intellij.execution.configurations.ConfigurationTypeUtil
import com.intellij.openapi.util.Ref
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.util.io.systemIndependentPath
import com.intellij.psi.PsiElement
import com.jetbrains.rider.aspire.launchProfiles.getFirstOrNullLaunchProfileProfile
import com.jetbrains.rider.aspire.run.AspireRunnableProjectKinds
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
        val profile = LaunchSettingsJsonService.Companion
            .getInstance(context.project)
            .getFirstOrNullLaunchProfileProfile(runnableProject)

        configuration.parameters.setUpFromRunnableProject(
            runnableProject,
            projectOutput,
            profile
        )

        return true
    }
}