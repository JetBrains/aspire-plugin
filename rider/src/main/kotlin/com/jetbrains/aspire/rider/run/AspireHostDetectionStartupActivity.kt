package com.jetbrains.aspire.rider.run

import com.intellij.openapi.components.serviceAsync
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity

/**
 * Instantiates [AspireHostWorkspaceDetector] (wiring its Workspace Model subscription) and reports
 * the app hosts that are already present in the solution.
 */
internal class AspireHostDetectionStartupActivity : ProjectActivity {
    override suspend fun execute(project: Project) {
        project.serviceAsync<AspireHostWorkspaceDetector>().scanExisting()
    }
}
