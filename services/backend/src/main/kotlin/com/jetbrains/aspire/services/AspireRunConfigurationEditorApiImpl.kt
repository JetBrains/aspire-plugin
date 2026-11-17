package com.jetbrains.aspire.services

import com.intellij.openapi.project.Project
import com.jetbrains.aspire.run.AspireRunConfigurationEditorApi

internal class AspireRunConfigurationEditorApiImpl : AspireRunConfigurationEditorApi {
    override suspend fun openCliRunConfigurationEditor(project: Project) {
        AspireCliRunConfigurationEditorLauncher.getInstance(project).open()
    }
}