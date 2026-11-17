package com.jetbrains.aspire.services

import com.intellij.execution.services.ServiceViewEmptyTreeSuggestion
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DataContext
import com.jetbrains.aspire.AspireIcons
import java.awt.event.InputEvent

@Suppress("JetBrainsInternalApiUsage")
internal object AspireEmptyTreeSuggestion : ServiceViewEmptyTreeSuggestion {
    override val weight = 0
    override val icon = AspireIcons.RunConfig
    override val text = AspireServicesBundle.message("service.aspire.create.run.configuration")

    override fun onActivate(dataContext: DataContext, inputEvent: InputEvent?) {
        val project = CommonDataKeys.PROJECT.getData(dataContext) ?: return
        AspireCliRunConfigurationEditorOpener.getInstance(project).open()
    }
}
