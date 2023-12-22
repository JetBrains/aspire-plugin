package me.rafaelldi.aspire.services

import com.intellij.execution.services.ServiceViewDescriptor
import com.intellij.ide.projectView.PresentationData
import com.intellij.ui.SimpleTextAttributes
import com.intellij.ui.components.JBTabbedPane
import icons.RiderIcons
import me.rafaelldi.aspire.AspireBundle
import me.rafaelldi.aspire.services.components.EnvironmentVariablePanel
import me.rafaelldi.aspire.services.components.SessionDashboardPanel
import java.awt.BorderLayout
import javax.swing.JPanel
import kotlin.io.path.Path
import kotlin.io.path.nameWithoutExtension

class SessionServiceViewDescriptor(private val sessionData: SessionServiceData) : ServiceViewDescriptor {

    private val projectName = Path(sessionData.sessionModel.projectPath).nameWithoutExtension

    override fun getPresentation() = PresentationData().apply {
        setIcon(RiderIcons.RunConfigurations.DotNetProject)
        addText(projectName, SimpleTextAttributes.REGULAR_ATTRIBUTES)
    }

    override fun getContentComponent(): JPanel {
        val tabs = JBTabbedPane()
        tabs.addTab(AspireBundle.getMessage("service.tab.Information"), SessionDashboardPanel(sessionData))
        tabs.addTab(AspireBundle.getMessage("service.tab.EnvironmentVariables"), EnvironmentVariablePanel(sessionData))
        return JPanel(BorderLayout()).apply {
            add(tabs, BorderLayout.CENTER)
        }
    }
}