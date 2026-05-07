@file:Suppress("UnstableApiUsage")

package com.jetbrains.aspire.dashboard

import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import com.intellij.openapi.components.service
import com.intellij.openapi.util.Disposer
import com.intellij.platform.util.coroutines.childScope
import com.jetbrains.aspire.worker.AspireWorker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.plus
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
@Service(Service.Level.PROJECT)
internal class AspireWorkerViewModelManager(private val project: Project, private val cs: CoroutineScope) : Disposable {
    companion object {
        fun getInstance(project: Project): AspireWorkerViewModelManager = project.service()
    }

    private val vm by lazy {
        val vmCs = cs.childScope(javaClass.name)
        val workerVm = AspireWorkerViewModel(
            project,
            vmCs + Dispatchers.Default,
            AspireWorker.getInstance(project)
        )
        Disposer.register(this, workerVm)
        workerVm
    }

    fun getOrCreate(): AspireWorkerViewModel {
        return vm
    }

    override fun dispose() {
    }
}