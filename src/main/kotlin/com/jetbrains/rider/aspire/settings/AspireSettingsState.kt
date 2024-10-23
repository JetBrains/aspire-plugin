package com.jetbrains.rider.aspire.settings

import com.intellij.openapi.components.BaseState

class AspireSettingsState : BaseState() {
    var doNotLaunchBrowserForProjects by property(false)
    var connectToDatabase by property(true)
}