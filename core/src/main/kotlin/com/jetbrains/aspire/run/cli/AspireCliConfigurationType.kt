package com.jetbrains.aspire.run.cli

import com.intellij.execution.configurations.ConfigurationTypeBase
import com.jetbrains.aspire.AspireCoreBundle
import com.jetbrains.aspire.AspireIcons
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
class AspireCliConfigurationType : ConfigurationTypeBase(
    ID,
    AspireCoreBundle.message("run.configuration.cli.name"),
    AspireCoreBundle.message("run.configuration.cli.description"),
    AspireIcons.RunConfig
) {
    companion object {
        const val ID = "AspireCliConfiguration"
    }

    val factory = AspireCliConfigurationFactory(this)

    init {
        addFactory(factory)
    }
}
