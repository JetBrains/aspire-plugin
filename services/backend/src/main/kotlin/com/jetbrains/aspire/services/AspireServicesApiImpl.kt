package com.jetbrains.aspire.services

import com.intellij.openapi.project.Project

internal class AspireServicesApiImpl : AspireServicesApi {
    override suspend fun openCliRunConfigurationEditor(project: Project) {
        AspireCliRunConfigurationEditorLauncher.getInstance(project).open()
    }
}