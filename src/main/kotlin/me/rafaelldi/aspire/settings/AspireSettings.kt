package me.rafaelldi.aspire.settings

import com.intellij.openapi.components.*

@State(
    name = "me.rafaelldi.aspire.settings.AspireSettings",
    storages = [(Storage("AspireSettings.xml"))]
)
@Service
class AspireSettings : SimplePersistentStateComponent<AspireSettingsState>(AspireSettingsState()) {
    companion object {
        fun getInstance() = service<AspireSettings>()
    }

    var checkForNewVersions
        get() = state.checkForNewVersions
        set(value) {
            state.checkForNewVersions = value
        }
    var connectToDatabase
        get() = state.connectToDatabase
        set(value) {
            state.connectToDatabase = value
        }
}