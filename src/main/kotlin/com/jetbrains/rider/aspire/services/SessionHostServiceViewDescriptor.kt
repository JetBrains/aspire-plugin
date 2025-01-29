package com.jetbrains.rider.aspire.services

import com.intellij.execution.services.ServiceViewDescriptor
import com.intellij.ide.projectView.PresentationData
import com.intellij.ui.SimpleTextAttributes
import com.jetbrains.rider.aspire.AspireBundle
import com.jetbrains.rider.aspire.AspireIcons

class SessionHostServiceViewDescriptor() : ServiceViewDescriptor {
    override fun getPresentation() = PresentationData().apply {
        setIcon(AspireIcons.Service)
        @Suppress("DialogTitleCapitalization")
        addText(AspireBundle.message("service.sessionHost.name"), SimpleTextAttributes.REGULAR_ATTRIBUTES)
    }
}