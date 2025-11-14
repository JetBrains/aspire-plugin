package com.jetbrains.rider.aspire.actions

import com.intellij.ide.actions.ContextHelpAction
import com.intellij.openapi.actionSystem.DataContext

class AspireHelpAction: ContextHelpAction() {
    override fun getHelpId(dataContext: DataContext?) = "com.jetbrains.rider.aspire.main"
}