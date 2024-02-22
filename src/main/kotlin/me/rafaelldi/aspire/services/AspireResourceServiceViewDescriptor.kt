@file:Suppress("UnstableApiUsage")

package me.rafaelldi.aspire.services

import com.intellij.execution.services.ServiceViewDescriptor
import com.intellij.ide.projectView.PresentationData
import com.intellij.openapi.rd.util.withUiContext
import com.intellij.ui.SimpleTextAttributes
import com.intellij.ui.components.JBTabbedPane
import com.jetbrains.rd.util.threading.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import me.rafaelldi.aspire.AspireBundle
import me.rafaelldi.aspire.services.components.ResourceConsolePanel
import me.rafaelldi.aspire.services.components.ResourceDashboardPanel
import me.rafaelldi.aspire.services.components.ResourceMetricPanel
import me.rafaelldi.aspire.settings.AspireSettings
import me.rafaelldi.aspire.util.getIcon
import java.awt.BorderLayout
import javax.swing.JPanel
import kotlin.time.Duration.Companion.seconds

class AspireResourceServiceViewDescriptor(
    private val resourceData: AspireResourceServiceData
) : ServiceViewDescriptor {

    private val metricPanel by lazy { ResourceMetricPanel() }

    private val mainPanel by lazy {
        val tabs = JBTabbedPane()
        tabs.addTab(AspireBundle.getMessage("service.tab.dashboard"), ResourceDashboardPanel(resourceData))
        tabs.addTab(AspireBundle.getMessage("service.tab.console"), ResourceConsolePanel(resourceData))
        if (AspireSettings.getInstance().collectTelemetry) {
            tabs.addTab(AspireBundle.getMessage("service.tab.metrics"), metricPanel)
        }

        JPanel(BorderLayout()).apply {
            add(tabs, BorderLayout.CENTER)
        }
    }

    init {
        if (AspireSettings.getInstance().collectTelemetry) {
            resourceData.getLifetime().launch(Dispatchers.Default) {
                while (true) {
                    delay(1.seconds)
                    withUiContext {
                        update()
                    }
                }
            }
        }
    }

    override fun getPresentation() = PresentationData().apply {
        val icon = getIcon(resourceData.resourceType, resourceData.isRunning)
        setIcon(icon)
        addText(resourceData.displayName, SimpleTextAttributes.REGULAR_ATTRIBUTES)
    }

    override fun getContentComponent() = mainPanel

    private fun update() {
//        val metrics = sessionData.sessionModel.metrics.toMap()
//        metricPanel.updateMetrics(metrics)
    }
}