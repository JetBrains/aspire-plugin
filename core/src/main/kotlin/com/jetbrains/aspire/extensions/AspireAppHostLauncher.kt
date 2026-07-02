package com.jetbrains.aspire.extensions

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.project.Project
import com.jetbrains.aspire.worker.AspireAppHost

interface AspireAppHostLauncher {
    companion object {
        private val EP_NAME = ExtensionPointName<AspireAppHostLauncher>("com.jetbrains.aspire.appHostLauncher")

        fun getInstance(): AspireAppHostLauncher? = EP_NAME.extensionList.singleOrNull()
    }

    suspend fun launchAppHost(appHost: AspireAppHost, underDebug: Boolean, project: Project)
    suspend fun stopAppHost(appHost: AspireAppHost, project: Project)
}
