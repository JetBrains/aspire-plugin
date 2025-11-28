package com.jetbrains.aspire.settings

import com.intellij.openapi.options.BoundConfigurable
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.dsl.builder.Cell
import com.intellij.ui.dsl.builder.bindSelected
import com.intellij.ui.dsl.builder.panel
import com.intellij.ui.dsl.builder.selected
import com.jetbrains.aspire.AspireBundle

internal class AspireConfigurable : BoundConfigurable(AspireBundle.message("configurable.Aspire")) {
    private val settings get() = AspireSettings.getInstance()

    private lateinit var connectToDatabase: Cell<JBCheckBox>

    override fun createPanel() = panel {
        row {
            checkBox(AspireBundle.message("configurable.Aspire.do.not.launch.browser"))
                .bindSelected(settings::doNotLaunchBrowserForProjects)
        }
        row {
            checkBox(AspireBundle.message("configurable.Aspire.connect.to.dcp.via.https"))
                .bindSelected(settings::connectToDcpViaHttps)
        }
        group(AspireBundle.message("configurable.Aspire.dashboard")) {
            row {
                checkBox(AspireBundle.message("configurable.Aspire.show.sensitive.properties"))
                    .bindSelected(settings::showSensitiveProperties)
            }
            row {
                checkBox(AspireBundle.message("configurable.Aspire.show.environment.variables"))
                    .bindSelected(settings::showEnvironmentVariables)
            }
            row {
                checkBox(AspireBundle.message("configurable.Aspire.open.console.view"))
                    .bindSelected(settings::openConsoleView)
            }
        }
        group(AspireBundle.message("configurable.Aspire.databases")) {
            row {
                connectToDatabase = checkBox(AspireBundle.message("configurable.Aspire.connect.to.database"))
                    .bindSelected(settings::connectToDatabase)
            }
            row {
                checkBox(AspireBundle.message("configurable.Aspire.check.resource.name.for.database"))
                    .bindSelected(settings::checkResourceNameForDatabase)
            }.enabledIf(connectToDatabase.selected)
        }
    }
}