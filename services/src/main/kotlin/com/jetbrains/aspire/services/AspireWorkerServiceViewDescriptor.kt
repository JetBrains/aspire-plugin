package com.jetbrains.aspire.services

import com.intellij.execution.services.ServiceViewDescriptor
import com.intellij.ide.projectView.PresentationData
import com.intellij.ui.SimpleTextAttributes

class AspireWorkerServiceViewDescriptor : ServiceViewDescriptor {
    override fun getPresentation() = PresentationData().apply {
        setIcon(AspireServicesIcons.Service)
        addText(AspireServicesBundle.message("service.aspire.worker.name"), SimpleTextAttributes.REGULAR_ATTRIBUTES)
    }
}
