package com.jetbrains.aspire.worker

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.project.Project

interface AspireAppHostLauncher {
    companion object {
        private val EP_NAME = ExtensionPointName<AspireAppHostLauncher>("com.jetbrains.aspire.appHostLauncher")

        fun getStarter(): AspireAppHostLauncher? = EP_NAME.extensionList.singleOrNull()
    }

    suspend fun launchAppHost(appHost: AspireAppHost, underDebug: Boolean, project: Project)
    suspend fun stopAppHost(appHost: AspireAppHost, project: Project)
}