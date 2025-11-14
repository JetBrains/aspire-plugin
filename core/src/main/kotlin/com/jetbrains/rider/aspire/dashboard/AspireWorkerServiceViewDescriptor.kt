package com.jetbrains.rider.aspire.dashboard

import com.intellij.execution.services.ServiceViewDescriptor
import com.intellij.ide.projectView.PresentationData
import com.intellij.ui.SimpleTextAttributes
import com.jetbrains.rider.aspire.AspireBundle
import com.jetbrains.rider.aspire.AspireIcons

class AspireWorkerServiceViewDescriptor : ServiceViewDescriptor {
    override fun getPresentation() = PresentationData().apply {
        setIcon(AspireIcons.Service)
        addText(AspireBundle.message("service.aspire.worker.name"), SimpleTextAttributes.REGULAR_ATTRIBUTES)
    }
}