package com.jetbrains.rider.aspire.graph

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.jetbrains.rider.aspire.services.AspireHost

@Service(Service.Level.PROJECT)
class ResourceGraphService {
    companion object {
        fun getInstance(project: Project): ResourceGraphService = project.service()
    }

    fun showResourceGraph(aspireHost: AspireHost) {

    }
}