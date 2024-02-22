@file:Suppress("UnstableApiUsage")

package me.rafaelldi.aspire.services

import com.intellij.execution.services.ServiceViewDescriptor
import com.intellij.ide.projectView.PresentationData
import com.intellij.ui.SimpleTextAttributes
import com.intellij.ui.components.JBTabbedPane
import me.rafaelldi.aspire.AspireBundle
import me.rafaelldi.aspire.services.components.ResourceDashboardPanel
import me.rafaelldi.aspire.util.getIcon
import java.awt.BorderLayout
import javax.swing.JPanel

class AspireResourceServiceViewDescriptor(private val resourceData: AspireResourceServiceData) : ServiceViewDescriptor {

//    private val dashboardPanel by lazy { SessionDashboardPanel(resourceData) }

    private val mainPanel by lazy {
        val tabs = JBTabbedPane()
        tabs.addTab(AspireBundle.getMessage("service.tab.dashboard"), ResourceDashboardPanel(resourceData))
        JPanel(BorderLayout()).apply {
            add(tabs, BorderLayout.CENTER)
        }
    }

//    private val projectName = Path(sessionData.resourceModel.name).nameWithoutExtension

//    private val metricPanel = SessionMetricPanel()

    init {
//        if (AspireSettings.getInstance().collectTelemetry) {
//            sessionData.sessionLifetime.launch(Dispatchers.Default) {
//                while (true) {
//                    delay(1.seconds)
//                    withUiContext {
//                        update()
//                    }
//                }
//            }
//        }
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