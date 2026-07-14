package com.jetbrains.aspire.rider.run.file

import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity

/**
 * Creates default file-based Aspire run configurations from [ASPIRE_CONFIG_JSON_NAME] on solution load.
 */
internal class AspireFileConfigurationStartupActivity : ProjectActivity {
    override suspend fun execute(project: Project) {
        AspireFileConfigurationGenerator.getInstance(project).generateDefaultConfigurations()
    }
}
