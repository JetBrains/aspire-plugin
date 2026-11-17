package com.jetbrains.aspire.run

import com.intellij.openapi.components.serviceAsync
import com.intellij.openapi.project.Project
import com.intellij.util.application
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
interface AspireRunConfigurationEditorApi {
    companion object {
        suspend fun getInstance(): AspireRunConfigurationEditorApi = application.serviceAsync()
    }

    /**
     * Opens the Run/Debug Configurations dialog for the Aspire CLI configuration type.
     */
    suspend fun openCliRunConfigurationEditor(project: Project)
}