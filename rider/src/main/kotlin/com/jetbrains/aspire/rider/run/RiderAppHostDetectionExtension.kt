package com.jetbrains.aspire.rider.run

import com.intellij.openapi.project.Project
import com.jetbrains.aspire.extensions.AppHostDetectionExtension
import java.nio.file.Path

internal class RiderAppHostDetectionExtension : AppHostDetectionExtension {
    override fun hasAppHost(project: Project, appHostFilePath: Path): Boolean =
        AspireHostWorkspaceDetector.getInstance(project).hasAppHost(appHostFilePath)
}
