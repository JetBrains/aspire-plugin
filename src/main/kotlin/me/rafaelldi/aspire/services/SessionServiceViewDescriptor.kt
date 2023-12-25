package me.rafaelldi.aspire.services

import com.intellij.execution.services.ServiceViewDescriptor
import com.intellij.ide.projectView.PresentationData
import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project
import com.intellij.ui.SimpleTextAttributes
import com.intellij.ui.components.JBTabbedPane
import icons.RiderIcons
import me.rafaelldi.aspire.AspireBundle
import me.rafaelldi.aspire.otel.OtelMetric
import me.rafaelldi.aspire.otel.OtelMetricListener
import me.rafaelldi.aspire.services.components.EnvironmentVariablePanel
import me.rafaelldi.aspire.services.components.SessionDashboardPanel
import me.rafaelldi.aspire.services.components.SessionMetricPanel
import me.rafaelldi.aspire.settings.AspireSettings
import java.awt.BorderLayout
import javax.swing.JPanel
import kotlin.io.path.Path
import kotlin.io.path.nameWithoutExtension

class SessionServiceViewDescriptor(project: Project, private val sessionData: SessionServiceData) :
    ServiceViewDescriptor, OtelMetricListener, Disposable {

    private val serviceName = sessionData.sessionModel.telemetryServiceName
    private val projectName = Path(sessionData.sessionModel.projectPath).nameWithoutExtension

    private val metricPanel = SessionMetricPanel()

    init {
        project.messageBus.connect(this).subscribe(OtelMetricListener.TOPIC, this)
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

    override fun onMetricsUpdated(metrics: Map<String, MutableMap<Pair<String, String>, OtelMetric>>) {
        if (serviceName == null) return
        val serviceMetrics = metrics[serviceName] ?: return
        metricPanel.updateMetrics(serviceMetrics)
    }

    override fun dispose() {
    }
}