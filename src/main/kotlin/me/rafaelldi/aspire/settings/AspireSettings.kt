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

    var showServices
        get() = state.showServices
        set(value) {
            state.showServices = value
        }
    var collectTelemetry
        get() = state.collectTelemetry
        set(value) {
            state.collectTelemetry = value
        }
}