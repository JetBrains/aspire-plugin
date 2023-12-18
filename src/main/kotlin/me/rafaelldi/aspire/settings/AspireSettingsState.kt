package me.rafaelldi.aspire.settings

import com.intellij.openapi.components.BaseState

class AspireSettingsState : BaseState() {
    var showServices by property(false)
}