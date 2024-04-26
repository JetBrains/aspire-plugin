package me.rafaelldi.aspire.run

import com.intellij.execution.configurations.ConfigurationTypeBase
import me.rafaelldi.aspire.AspireIcons

class AspireHostConfigurationType : ConfigurationTypeBase(
    ID,
    "Aspire Host",
    "Aspire Host configuration",
    AspireIcons.RunConfig
) {
    companion object {
        const val ID = "AspireHostConfiguration"
    }

    private val factory = AspireHostConfigurationFactory(this)

    init {
        addFactory(factory)
    }

    override fun getHelpTopic() = "me.rafaelldi.aspire.run-config"
}