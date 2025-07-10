package com.jetbrains.rider.aspire.projectTemplates

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project

@Service(Service.Level.PROJECT)
class AspireProjectTemplateService(private val project: Project) {
    companion object {
        fun getInstance(project: Project): AspireProjectTemplateService = project.service()
        private val LOG = logger<AspireProjectTemplateService>()
    }

    fun createHotProjectFromTemplate() {
        LOG.info("Generate Aspire projects for the solution")
    }
}