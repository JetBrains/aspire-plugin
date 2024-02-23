package me.rafaelldi.aspire.settings

import com.intellij.openapi.components.BaseState

class AspireSettingsState : BaseState() {
    var checkForNewVersions by property(true)
    var collectTelemetry by property(false)
}