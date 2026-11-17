package com.jetbrains.aspire.services

import com.intellij.execution.services.ServiceViewNonActivatingDescriptor
import com.intellij.execution.services.ServiceViewToolWindowDescriptor
import com.intellij.execution.services.SimpleServiceViewDescriptor
import com.intellij.openapi.util.NlsContexts
import javax.swing.Icon

internal const val ASPIRE_TOOLWINDOW_ID: String = "Aspire"

internal object AspireMainServiceViewDescriptor : SimpleServiceViewDescriptor(
    "Aspire",
    AspireServicesIcons.Service,
    ASPIRE_TOOLWINDOW_ID
), ServiceViewNonActivatingDescriptor, ServiceViewToolWindowDescriptor {
    override fun getToolWindowId() = id

    override fun getToolWindowIcon(): Icon = AspireServicesIcons.Service

    override fun getStripeTitle(): @NlsContexts.TabTitle String = presentation.presentableText!!
}
