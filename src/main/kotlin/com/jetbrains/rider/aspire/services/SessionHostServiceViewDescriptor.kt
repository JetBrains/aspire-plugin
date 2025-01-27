package com.jetbrains.rider.aspire.services

import com.intellij.execution.services.ServiceViewDescriptor
import com.intellij.ide.projectView.PresentationData
import com.intellij.ui.BadgeIconSupplier
import com.intellij.ui.SimpleTextAttributes
import com.jetbrains.rider.aspire.AspireBundle
import com.jetbrains.rider.aspire.AspireIcons

class SessionHostServiceViewDescriptor(private val sessionHost: SessionHost) : ServiceViewDescriptor {
    override fun getPresentation() = PresentationData().apply {
        var icon = AspireIcons.Service
        if (sessionHost.isActive) {
            icon = BadgeIconSupplier(icon).liveIndicatorIcon
        }
        setIcon(icon)
        @Suppress("DialogTitleCapitalization")
        addText(AspireBundle.message("service.sessionHost.name"), SimpleTextAttributes.REGULAR_ATTRIBUTES)
    }
}