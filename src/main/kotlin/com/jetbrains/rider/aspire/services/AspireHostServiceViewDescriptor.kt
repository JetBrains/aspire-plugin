package com.jetbrains.rider.aspire.services

import com.intellij.execution.services.ServiceViewDescriptor
import com.intellij.ide.projectView.PresentationData
import com.intellij.ui.SimpleTextAttributes
import com.jetbrains.rider.aspire.AspireIcons

class AspireHostServiceViewDescriptor(private val aspireHost: AspireHost) : ServiceViewDescriptor {
    override fun getPresentation() = PresentationData().apply {
        var icon = AspireIcons.Service
        setIcon(icon)
        addText(aspireHost.displayName, SimpleTextAttributes.REGULAR_ATTRIBUTES)
    }
}