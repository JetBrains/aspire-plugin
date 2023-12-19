package me.rafaelldi.aspire.settings

import com.intellij.openapi.options.BoundConfigurable
import com.intellij.ui.dsl.builder.bindSelected
import com.intellij.ui.dsl.builder.panel
import me.rafaelldi.aspire.AspireBundle

class AspireConfigurable: BoundConfigurable(AspireBundle.message("configurable.Aspire")) {
    private val settings get() = AspireSettings.getInstance()

    override fun createPanel() = panel {
        row {
            checkBox(AspireBundle.message("configurable.Aspire.show.service"))
                .bindSelected(settings::showServices)
        }
        row {
            checkBox(AspireBundle.message("configurable.Aspire.collect.telemetry"))
                .bindSelected(settings::collectTelemetry)
        }
    }
}