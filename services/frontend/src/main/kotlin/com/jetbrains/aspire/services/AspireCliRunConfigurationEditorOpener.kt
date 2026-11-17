package com.jetbrains.aspire.services

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

@Service(Service.Level.PROJECT)
internal class AspireCliRunConfigurationEditorOpener(private val project: Project, private val cs: CoroutineScope) {
    companion object {
        fun getInstance(project: Project): AspireCliRunConfigurationEditorOpener = project.service()
    }

    fun open() {
        cs.launch {
            AspireServicesApi.getInstance().openCliRunConfigurationEditor(project)
        }
    }
}