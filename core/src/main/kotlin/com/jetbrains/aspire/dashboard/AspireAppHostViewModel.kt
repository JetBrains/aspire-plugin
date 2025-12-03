@file:Suppress("UnstableApiUsage")

package com.jetbrains.aspire.dashboard

import com.intellij.openapi.project.Project
import com.intellij.platform.util.coroutines.childScope
import com.jetbrains.aspire.worker.AspireAppHost
import kotlinx.coroutines.CoroutineScope

internal class AspireAppHostViewModel(
    parentCs: CoroutineScope,
    private val appHost: AspireAppHost
) {
    private val cs: CoroutineScope = parentCs.childScope("Aspire AppHost VM")

    private val descriptor by lazy { AspireAppHostServiceViewDescriptor(this) }

    fun getViewDescriptor(project: Project) = descriptor
}