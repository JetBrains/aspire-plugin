package me.rafaelldi.aspire.settings

import com.intellij.openapi.components.BaseState

class AspireSettingsState : BaseState() {
    var checkForNewVersions by property(true)
    var showServices by property(false)
    var collectTelemetry by property(false)
}