package com.github.rafaelldi.aspireplugin.run

import com.intellij.openapi.project.Project
import com.jetbrains.rider.run.configurations.DotNetConfigurationFactoryBase
import com.jetbrains.rider.run.configurations.project.DotNetStartBrowserParameters

class AspireHostConfigurationFactory(type: AspireHostConfigurationType) :
    DotNetConfigurationFactoryBase<AspireHostConfiguration>(type) {
    override fun getId() = "Aspire Host"

    override fun createTemplateConfiguration(project: Project) = AspireHostConfiguration(
        project,
        this,
        "Aspire Host",
        AspireHostConfigurationParameters(
            project, "", true, DotNetStartBrowserParameters()
        )
    )
}