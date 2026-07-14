package com.jetbrains.aspire.rider.run.file

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * The name of the Aspire configuration file that replaced `apphost.run.json` for file-based app hosts.
 */
internal const val ASPIRE_CONFIG_JSON_NAME = "aspire.config.json"

/**
 * Model of the `aspire.config.json` file.
 *
 * Only [appHost] is modeled here; the `profiles` are read through
 * [com.jetbrains.rider.run.configurations.launchSettings.LaunchSettingsJsonService], which already
 * models them as [com.jetbrains.rider.run.configurations.launchSettings.LaunchSettingsJson.Profile].
 *
 * Example:
 * ```json
 * {
 *   "appHost": { "path": "apphost.cs" },
 *   "profiles": {
 *     "http": {
 *       "applicationUrl": "http://localhost:15101",
 *       "environmentVariables": { "ASPNETCORE_ENVIRONMENT": "Development" }
 *     }
 *   }
 * }
 * ```
 */
@Serializable
internal data class AspireConfigJson(
    @SerialName("appHost") val appHost: AppHost? = null
) {
    @Serializable
    internal data class AppHost(
        @SerialName("path") val path: String? = null
    )
}
