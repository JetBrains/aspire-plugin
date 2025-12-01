package com.jetbrains.aspire.dashboard

import com.intellij.execution.services.ServiceViewDescriptor
import com.intellij.ide.projectView.PresentationData
import com.intellij.ui.SimpleTextAttributes
import com.jetbrains.aspire.AspireCoreBundle
import com.jetbrains.aspire.AspireIcons

class AspireWorkerServiceViewDescriptor : ServiceViewDescriptor {
    override fun getPresentation() = PresentationData().apply {
        setIcon(AspireIcons.Service)
        addText(AspireCoreBundle.message("service.aspire.worker.name"), SimpleTextAttributes.REGULAR_ATTRIBUTES)
    }
}