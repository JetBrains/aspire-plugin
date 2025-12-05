@file:Suppress("UnstableApiUsage")

package com.jetbrains.aspire.dashboard

import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project
import com.intellij.platform.util.coroutines.childScope
import com.jetbrains.aspire.worker.AspireResource2
import kotlinx.coroutines.CoroutineScope

class AspireResourceViewModel(
    private val project: Project,
    parentCs: CoroutineScope,
    private val resource: AspireResource2,
) : Disposable {
    private val cs: CoroutineScope = parentCs.childScope("Aspire Resource VM")

    private val descriptor by lazy { AspireResourceServiceViewDescriptor2(this) }

    val resourceId: String = resource.resourceId

    fun getViewDescriptor() = descriptor

    override fun dispose() {
    }
}