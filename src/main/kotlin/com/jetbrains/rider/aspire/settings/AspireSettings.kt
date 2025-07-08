package com.jetbrains.rider.aspire.settings

import com.intellij.openapi.components.*

@State(
    name = "com.jetbrains.rider.aspire.settings.AspireSettings",
    storages = [(Storage("AspireSettings.xml"))]
)
@Service
class AspireSettings : SimplePersistentStateComponent<AspireSettingsState>(AspireSettingsState()) {
    companion object {
        fun getInstance() = service<AspireSettings>()
    }

    var doNotLaunchBrowserForProjects
        get() = state.doNotLaunchBrowserForProjects
        set(value) {
            state.doNotLaunchBrowserForProjects = value
        }

    var connectToDatabase
        get() = state.connectToDatabase
        set(value) {
            state.connectToDatabase = value
        }

    var checkResourceNameForDatabase
        get() = state.checkResourceNameForDatabase
        set(value) {
            state.checkResourceNameForDatabase = value
        }

    var showSensitiveProperties
        get() = state.showSensitiveProperties
        set(value) {
            state.showSensitiveProperties = value
        }

    var openConsoleView
        get() = state.openConsoleView
        set(value) {
            state.openConsoleView = value
        }
}