package com.jetbrains.rider.aspire.settings

import com.intellij.openapi.components.BaseState

class AspireSettingsState : BaseState() {
    var connectToDatabase by property(true)
}