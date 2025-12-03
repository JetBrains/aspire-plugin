package com.jetbrains.aspire.worker

import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import com.intellij.openapi.components.service

@Service(Service.Level.PROJECT)
internal class AspireWorkerService(private val project: Project) {
    companion object {
        fun getInstance(project: Project): AspireWorkerService = project.service()
    }

    fun doWork() {
        TODO()
    }
}