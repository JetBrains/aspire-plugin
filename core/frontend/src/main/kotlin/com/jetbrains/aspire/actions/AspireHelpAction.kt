package com.jetbrains.aspire.actions

import com.intellij.ide.actions.ContextHelpAction
import com.intellij.openapi.actionSystem.DataContext

internal class AspireHelpAction : ContextHelpAction() {
    override fun getHelpId(dataContext: DataContext?) = "com.jetbrains.aspire.main"
}
