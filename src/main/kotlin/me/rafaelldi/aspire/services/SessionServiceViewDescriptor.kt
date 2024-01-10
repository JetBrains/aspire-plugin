package me.rafaelldi.aspire.services

import com.intellij.execution.services.ServiceViewDescriptor
import com.intellij.ide.projectView.PresentationData
import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project
import com.intellij.openapi.rd.util.withUiContext
import com.intellij.openapi.util.Disposer
import com.intellij.ui.SimpleTextAttributes
import com.intellij.ui.components.JBTabbedPane
import com.jetbrains.rd.util.threading.coroutines.launch
import icons.RiderIcons
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import me.rafaelldi.aspire.AspireBundle
import me.rafaelldi.aspire.services.components.ConsolePanel
import me.rafaelldi.aspire.services.components.EnvironmentVariablePanel
import me.rafaelldi.aspire.services.components.SessionDashboardPanel
import me.rafaelldi.aspire.services.components.SessionMetricPanel
import me.rafaelldi.aspire.settings.AspireSettings
import java.awt.BorderLayout
import javax.swing.JPanel
import kotlin.io.path.Path
import kotlin.io.path.nameWithoutExtension
import kotlin.time.Duration.Companion.seconds

class SessionServiceViewDescriptor(
    private val sessionData: SessionServiceData,
    private val project: Project
) : ServiceViewDescriptor, Disposable {

    private val projectName = Path(sessionData.sessionModel.projectPath).nameWithoutExtension

    private val metricPanel by lazy { SessionMetricPanel() }

    private val mainPanel by lazy {
        val tabs = JBTabbedPane()
        tabs.addTab(AspireBundle.getMessage("service.tab.information"), SessionDashboardPanel(sessionData))
        tabs.addTab(AspireBundle.getMessage("service.tab.environmentVariables"), EnvironmentVariablePanel(sessionData))

        val consolePanel = ConsolePanel(sessionData, project)
        Disposer.register(this, consolePanel)
        tabs.addTab(AspireBundle.getMessage("service.tab.console"), consolePanel)

        if (AspireSettings.getInstance().collectTelemetry) {
            tabs.addTab(AspireBundle.getMessage("service.tab.metrics"), metricPanel)
        }

        JPanel(BorderLayout()).apply {
            add(tabs, BorderLayout.CENTER)
        }
    }

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

    override fun getContentComponent() = mainPanel

    private fun update() {
        val metrics = sessionData.sessionModel.metrics.toMap()
        metricPanel.updateMetrics(metrics)
    }

    override fun dispose() {
    }
}