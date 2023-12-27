package me.rafaelldi.aspire.services

import com.intellij.execution.services.ServiceViewDescriptor
import com.intellij.ide.projectView.PresentationData
import com.intellij.openapi.rd.util.withUiContext
import com.intellij.ui.SimpleTextAttributes
import com.intellij.ui.components.JBTabbedPane
import com.jetbrains.rd.util.threading.coroutines.launch
import icons.RiderIcons
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import me.rafaelldi.aspire.AspireBundle
import me.rafaelldi.aspire.services.components.EnvironmentVariablePanel
import me.rafaelldi.aspire.services.components.SessionDashboardPanel
import me.rafaelldi.aspire.services.components.SessionMetricPanel
import me.rafaelldi.aspire.settings.AspireSettings
import java.awt.BorderLayout
import javax.swing.JPanel
import kotlin.io.path.Path
import kotlin.io.path.nameWithoutExtension
import kotlin.time.Duration.Companion.seconds

class SessionServiceViewDescriptor(private val sessionData: SessionServiceData) : ServiceViewDescriptor {

    private val projectName = Path(sessionData.sessionModel.projectPath).nameWithoutExtension

    private val metricPanel = SessionMetricPanel()

    init {
        if (AspireSettings.getInstance().collectTelemetry) {
            sessionData.sessionLifetime.launch(Dispatchers.Default) {
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
        setIcon(RiderIcons.RunConfigurations.DotNetProject)
        addText(projectName, SimpleTextAttributes.REGULAR_ATTRIBUTES)
    }

    override fun getContentComponent(): JPanel {
        val tabs = JBTabbedPane()
        tabs.addTab(AspireBundle.getMessage("service.tab.Information"), SessionDashboardPanel(sessionData))
        tabs.addTab(AspireBundle.getMessage("service.tab.EnvironmentVariables"), EnvironmentVariablePanel(sessionData))
        if (AspireSettings.getInstance().collectTelemetry) {
            tabs.addTab(AspireBundle.getMessage("service.tab.Metrics"), metricPanel)
        }
        return JPanel(BorderLayout()).apply {
            add(tabs, BorderLayout.CENTER)
        }
    }

    private fun update() {
        val metrics = sessionData.sessionModel.metrics.toMap()
        metricPanel.updateMetrics(metrics)
    }
}