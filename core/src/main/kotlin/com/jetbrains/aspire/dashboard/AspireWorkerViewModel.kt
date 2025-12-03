@file:Suppress("UnstableApiUsage")

package com.jetbrains.aspire.dashboard

import com.intellij.openapi.project.Project
import com.intellij.platform.util.coroutines.childScope
import com.jetbrains.aspire.worker.AspireWorkerService
import kotlinx.coroutines.CoroutineScope

internal class AspireWorkerViewModel(
    private val project: Project,
    parentCs: CoroutineScope,
    private val workerService: AspireWorkerService,
) {
    private val cs: CoroutineScope = parentCs.childScope("Aspire Worker VM")

    private val descriptor by lazy { AspireWorkerServiceViewDescriptor() }

    fun getViewDescriptor(project: Project) = descriptor
}