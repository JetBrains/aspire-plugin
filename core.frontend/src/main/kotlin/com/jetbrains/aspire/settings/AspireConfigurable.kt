package com.jetbrains.aspire.settings

import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.options.BoundConfigurable
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.dsl.builder.AlignX
import com.intellij.ui.dsl.builder.BottomGap
import com.intellij.ui.dsl.builder.Cell
import com.intellij.ui.dsl.builder.bindSelected
import com.intellij.ui.dsl.builder.bindText
import com.intellij.ui.dsl.builder.panel
import com.intellij.ui.dsl.builder.selected
import com.jetbrains.aspire.AspireCoreFrontendBundle

internal class AspireConfigurable : BoundConfigurable(AspireCoreFrontendBundle.message("configurable.Aspire")) {
    private val settings get() = AspireSettings.getInstance()

    private lateinit var connectToDatabase: Cell<JBCheckBox>

    override fun createPanel() = panel {
        row(AspireCoreFrontendBundle.message("configurable.Aspire.cli.path")) {
            val descriptor = FileChooserDescriptorFactory.singleFile()
                .withTitle(AspireCoreFrontendBundle.message("configurable.Aspire.cli.path.title"))
            @Suppress("UnstableApiUsage")
            textFieldWithBrowseButton(descriptor)
                .bindText(settings::aspireCliPath)
                .align(AlignX.FILL)
                .comment(AspireCoreFrontendBundle.message("configurable.Aspire.cli.path.comment"))
        }.bottomGap(BottomGap.MEDIUM)
        row {
            checkBox(AspireCoreFrontendBundle.message("configurable.Aspire.connect.to.dcp.via.https"))
                .bindSelected(settings::connectToDcpViaHttps)
        }
        group(AspireCoreFrontendBundle.message("configurable.Aspire.dotnet")) {
            row {
                checkBox(AspireCoreFrontendBundle.message("configurable.Aspire.force.build.of.apphost.referenced.projects"))
                    .bindSelected(settings::forceBuildOfAppHostReferencedProjects)
            }
            row {
                checkBox(AspireCoreFrontendBundle.message("configurable.Aspire.build.external.net.projects"))
                    .bindSelected(settings::buildExternalNetProjects)
            }
            row {
                checkBox(AspireCoreFrontendBundle.message("configurable.Aspire.do.not.launch.browser"))
                    .bindSelected(settings::doNotLaunchBrowserForProjects)
            }
        }
        group(AspireCoreFrontendBundle.message("configurable.Aspire.dashboard")) {
            row {
                checkBox(AspireCoreFrontendBundle.message("configurable.Aspire.show.hidden.resources"))
                    .bindSelected(settings::showHiddenResources)
            }
            row {
                checkBox(AspireCoreFrontendBundle.message("configurable.Aspire.show.sensitive.properties"))
                    .bindSelected(settings::showSensitiveProperties)
            }
            row {
                checkBox(AspireCoreFrontendBundle.message("configurable.Aspire.show.environment.variables"))
                    .bindSelected(settings::showEnvironmentVariables)
            }
            row {
                checkBox(AspireCoreFrontendBundle.message("configurable.Aspire.open.console.view"))
                    .bindSelected(settings::openConsoleView)
            }
        }
        group(AspireCoreFrontendBundle.message("configurable.Aspire.databases")) {
            row {
                connectToDatabase = checkBox(AspireCoreFrontendBundle.message("configurable.Aspire.connect.to.database"))
                    .bindSelected(settings::connectToDatabase)
            }
            row {
                checkBox(AspireCoreFrontendBundle.message("configurable.Aspire.check.resource.name.for.database"))
                    .bindSelected(settings::checkResourceNameForDatabase)
            }.enabledIf(connectToDatabase.selected)
        }
    }
}
