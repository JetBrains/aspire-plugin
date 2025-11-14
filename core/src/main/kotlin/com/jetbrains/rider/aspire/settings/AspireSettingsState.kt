package com.jetbrains.rider.aspire.settings

import com.intellij.openapi.components.BaseState

class AspireSettingsState : BaseState() {
    var doNotLaunchBrowserForProjects by property(false)
    var connectToDcpViaHttps by property(true)
    var connectToDatabase by property(true)
    var checkResourceNameForDatabase by property(false)
    var showSensitiveProperties by property(true)
    var showEnvironmentVariables by property(true)
    var openConsoleView by property(false)
}