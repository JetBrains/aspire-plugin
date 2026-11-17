package com.jetbrains.aspire.services

import com.intellij.execution.configurations.runConfigurationType
import com.intellij.execution.impl.EditConfigurationsDialog
import com.intellij.openapi.application.EDT
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.serviceAsync
import com.intellij.openapi.project.Project
import com.jetbrains.aspire.run.cli.AspireCliConfigurationType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Service(Service.Level.PROJECT)
internal class AspireCliRunConfigurationEditorLauncher(private val project: Project, private val cs: CoroutineScope) {
    companion object {
        suspend fun getInstance(project: Project): AspireCliRunConfigurationEditorLauncher = project.serviceAsync()
    }

    fun open() {
        cs.launch(Dispatchers.EDT) {
            val configurationType = runConfigurationType<AspireCliConfigurationType>()
            EditConfigurationsDialog(project, configurationType).show()
        }
    }
}