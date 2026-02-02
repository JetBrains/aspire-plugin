package com.jetbrains.aspire.settings

import com.intellij.openapi.options.BoundConfigurable
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.dsl.builder.Cell
import com.intellij.ui.dsl.builder.bindSelected
import com.intellij.ui.dsl.builder.panel
import com.intellij.ui.dsl.builder.selected
import com.jetbrains.aspire.AspireCoreBundle

internal class AspireConfigurable : BoundConfigurable(AspireCoreBundle.message("configurable.Aspire")) {
    private val settings get() = AspireSettings.getInstance()

    private lateinit var connectToDatabase: Cell<JBCheckBox>

    override fun createPanel() = panel {
        row {
            checkBox(AspireCoreBundle.message("configurable.Aspire.force.build.of.apphost.referenced.projects"))
                .bindSelected(settings::forceBuildOfAppHostReferencedProjects)
        }
        row {
            checkBox(AspireCoreBundle.message("configurable.Aspire.do.not.launch.browser"))
                .bindSelected(settings::doNotLaunchBrowserForProjects)
        }
        row {
            checkBox(AspireCoreBundle.message("configurable.Aspire.connect.to.dcp.via.https"))
                .bindSelected(settings::connectToDcpViaHttps)
        }
        group(AspireCoreBundle.message("configurable.Aspire.dashboard")) {
            row {
                checkBox(AspireCoreBundle.message("configurable.Aspire.show.sensitive.properties"))
                    .bindSelected(settings::showSensitiveProperties)
            }
            row {
                checkBox(AspireCoreBundle.message("configurable.Aspire.show.environment.variables"))
                    .bindSelected(settings::showEnvironmentVariables)
            }
            row {
                checkBox(AspireCoreBundle.message("configurable.Aspire.open.console.view"))
                    .bindSelected(settings::openConsoleView)
            }
        }
        group(AspireCoreBundle.message("configurable.Aspire.databases")) {
            row {
                connectToDatabase = checkBox(AspireCoreBundle.message("configurable.Aspire.connect.to.database"))
                    .bindSelected(settings::connectToDatabase)
            }
            row {
                checkBox(AspireCoreBundle.message("configurable.Aspire.check.resource.name.for.database"))
                    .bindSelected(settings::checkResourceNameForDatabase)
            }.enabledIf(connectToDatabase.selected)
        }
    }
}