package me.rafaelldi.aspire.services

import com.intellij.execution.services.ServiceViewDescriptor
import com.intellij.ide.projectView.PresentationData
import com.intellij.ui.SimpleTextAttributes
import icons.RiderIcons
import kotlin.io.path.Path
import kotlin.io.path.nameWithoutExtension

class SessionServiceViewDescriptor(private val sessionData: SessionServiceData) : ServiceViewDescriptor {

    private val projectName = Path(sessionData.sessionModel.projectPath).nameWithoutExtension

    override fun getPresentation() = PresentationData().apply {
        setIcon(RiderIcons.RunConfigurations.DotNetProject)
        addText(projectName, SimpleTextAttributes.REGULAR_ATTRIBUTES)
    }
}