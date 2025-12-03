package com.jetbrains.aspire.dashboard

import com.intellij.execution.services.ServiceViewDescriptor
import com.intellij.navigation.ItemPresentation
import com.intellij.openapi.actionSystem.DataProvider
import org.jetbrains.annotations.NonNls

internal class AspireAppHostServiceViewDescriptor(
    private val vm: AspireAppHostViewModel
) : ServiceViewDescriptor, DataProvider {
    override fun getPresentation(): ItemPresentation {
        TODO("Not yet implemented")
    }

    override fun getData(dataId: @NonNls String): Any? {
        TODO("Not yet implemented")
    }
}