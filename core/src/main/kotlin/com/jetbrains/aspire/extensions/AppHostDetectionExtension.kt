package com.jetbrains.aspire.extensions

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.project.Project
import org.jetbrains.annotations.ApiStatus
import java.nio.file.Path

@ApiStatus.Internal
interface AppHostDetectionExtension {
    companion object {
        private val EP_NAME = ExtensionPointName<AppHostDetectionExtension>("com.jetbrains.aspire.appHostDetectionExtension")

        fun hasAppHost(project: Project, appHostFilePath: Path): Boolean =
            EP_NAME.extensionList.any { it.hasAppHost(project, appHostFilePath) }
    }

    fun hasAppHost(project: Project, appHostFilePath: Path): Boolean
}
