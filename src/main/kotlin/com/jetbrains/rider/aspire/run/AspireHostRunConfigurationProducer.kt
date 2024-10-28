package com.jetbrains.rider.aspire.run

import com.intellij.execution.actions.ConfigurationContext
import com.intellij.execution.actions.LazyRunConfigurationProducer
import com.intellij.execution.configurations.ConfigurationFactory
import com.intellij.execution.configurations.ConfigurationTypeUtil
import com.intellij.openapi.util.Ref
import com.intellij.openapi.util.io.FileUtil
import com.intellij.psi.PsiElement
import com.jetbrains.rider.model.runnableProjectsModel
import com.jetbrains.rider.projectView.solution
import com.jetbrains.rider.projectView.workspace.getFile
import com.jetbrains.rider.run.configurations.getSelectedProject

class AspireHostRunConfigurationProducer : LazyRunConfigurationProducer<AspireHostConfiguration>() {
    override fun getConfigurationFactory(): ConfigurationFactory {
        return ConfigurationTypeUtil.findConfigurationType(AspireHostConfigurationType::class.java).factory
    }

    override fun isConfigurationFromContext(
        configuration: AspireHostConfiguration,
        context: ConfigurationContext
    ): Boolean {
        val item = context.getSelectedProject() ?: return false
        val projectFilePath = FileUtil.toSystemIndependentName(item.getFile()?.path ?: "")
        return projectFilePath == configuration.parameters.projectFilePath
    }

    override fun setupConfigurationFromContext(
        configuration: AspireHostConfiguration,
        context: ConfigurationContext,
        sourceElement: Ref<PsiElement?>
    ): Boolean {
        val item = context.getSelectedProject() ?: return false
        val projectFilePath = FileUtil.toSystemIndependentName(item.getFile()?.path ?: "")
        val runnableProjects = context.project.solution.runnableProjectsModel.projects.valueOrNull ?: return false
        val runnableProject = runnableProjects.firstOrNull { it.projectFilePath == projectFilePath  } ?: return false

        configuration.parameters.projectFilePath = runnableProject.projectFilePath

        return true
    }
}