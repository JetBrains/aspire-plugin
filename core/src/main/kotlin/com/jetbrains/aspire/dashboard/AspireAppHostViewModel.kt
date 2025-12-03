@file:Suppress("UnstableApiUsage")

package com.jetbrains.aspire.dashboard

import com.intellij.openapi.project.Project
import com.intellij.platform.util.coroutines.childScope
import com.jetbrains.aspire.worker.AspireAppHost
import kotlinx.coroutines.CoroutineScope
import kotlin.io.path.nameWithoutExtension

class AspireAppHostViewModel(
    parentCs: CoroutineScope,
    private val appHost: AspireAppHost
) {
    private val cs: CoroutineScope = parentCs.childScope("Aspire AppHost VM")

    private val descriptor by lazy { AspireAppHostServiceViewDescriptor(this) }

    val appHostMainFilePath = appHost.mainFilePath
    val displayName: String = appHostMainFilePath.nameWithoutExtension
    var isActive: Boolean = false
        private set

    fun getViewDescriptor(project: Project) = descriptor
}