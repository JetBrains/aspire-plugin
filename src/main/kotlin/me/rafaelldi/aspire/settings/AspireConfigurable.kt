package me.rafaelldi.aspire.settings

import com.intellij.openapi.options.BoundConfigurable
import com.intellij.ui.dsl.builder.bindSelected
import com.intellij.ui.dsl.builder.panel
import me.rafaelldi.aspire.AspireBundle

class AspireConfigurable: BoundConfigurable(AspireBundle.message("configurable.Aspire")) {
    private val settings get() = AspireSettings.getInstance()

    override fun createPanel() = panel {
        row {
            checkBox(AspireBundle.message("configurable.Aspire.check.new.version"))
                .bindSelected(settings::checkForNewVersions)
        }
        row {
            checkBox(AspireBundle.message("configurable.Aspire.connect.to.database"))
                .bindSelected(settings::connectToDatabase)
        }
    }
}