@file:Suppress("UnstableApiUsage")

package com.jetbrains.aspire.services.actions

import com.intellij.execution.configurations.runConfigurationType
import com.intellij.execution.impl.EditConfigurationsDialog
import com.intellij.execution.services.ServiceViewAddActionContributor
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAwareAction
import com.jetbrains.aspire.run.cli.AspireCliConfigurationType
import com.jetbrains.aspire.services.AspireMainServiceViewContributor

internal class AspireAddCliRunConfigurationAction : DumbAwareAction(), ServiceViewAddActionContributor {

    override fun getContributorClass(): Class<*> = AspireMainServiceViewContributor::class.java

    override fun getActionUpdateThread() = ActionUpdateThread.BGT

    override fun update(event: AnActionEvent) {
        event.presentation.isEnabledAndVisible = event.project != null
    }

    override fun actionPerformed(event: AnActionEvent) {
        val project = event.project ?: return
        val configurationType = runConfigurationType<AspireCliConfigurationType>()

        EditConfigurationsDialog(project, configurationType).show()
    }
}
