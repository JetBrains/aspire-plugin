package com.jetbrains.rider.aspire.settings

import com.intellij.openapi.components.BaseState

class AspireSettingsState : BaseState() {
    var doNotLaunchBrowserForProjects by property(false)
    var connectToDatabase by property(true)
    var checkResourceNameForDatabase by property(false)
    var showSensitiveProperties by property(false)
    var openConsoleView by property(false)
}