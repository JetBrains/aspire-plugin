package com.jetbrains.aspire.settings

import com.intellij.ide.settings.RemoteSettingInfo
import com.intellij.ide.settings.RemoteSettingInfoProvider

/**
 * Synchronizes [AspireSettings] between the frontend and the backend in split mode.
 *
 * The initial value comes from the backend: most of the settings (CLI path, DCP connection, build options)
 * describe the host machine where the AppHost runs.
 */
internal class AspireRemoteSettingInfoProvider : RemoteSettingInfoProvider {
    private val aspireSettingsKey = AspireSettings.COMPONENT_NAME.replace('.', '-')

    override fun getRemoteSettingsInfo(): Map<String, RemoteSettingInfo> = mapOf(
        aspireSettingsKey to RemoteSettingInfo(RemoteSettingInfo.Direction.InitialFromBackend)
    )
}
