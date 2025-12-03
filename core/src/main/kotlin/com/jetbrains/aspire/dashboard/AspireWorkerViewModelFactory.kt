@file:Suppress("UnstableApiUsage")

package com.jetbrains.aspire.dashboard

import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import com.intellij.openapi.components.service
import com.intellij.platform.util.coroutines.childScope
import com.jetbrains.aspire.worker.AspireWorkerService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.plus

@Service(Service.Level.PROJECT)
internal class AspireWorkerViewModelFactory(private val project: Project, private val cs: CoroutineScope) {
    companion object {
        fun getInstance(project: Project): AspireWorkerViewModelFactory = project.service()
    }

    fun create(): AspireWorkerViewModel {
        val vmCs = cs.childScope(javaClass.name)
        return AspireWorkerViewModel(
            vmCs + Dispatchers.Default,
            AspireWorkerService.getInstance(project)
        )
    }
}