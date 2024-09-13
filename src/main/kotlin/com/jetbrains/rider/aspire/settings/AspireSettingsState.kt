package com.jetbrains.rider.aspire.settings

import com.intellij.openapi.components.BaseState

class AspireSettingsState : BaseState() {
    var checkForNewVersions by property(true)
    var connectToDatabase by property(true)
}