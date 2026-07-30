package com.jetbrains.aspire.rider.util

import com.intellij.openapi.diagnostic.Logger
import com.jetbrains.rider.model.RdTargetFrameworkId
import com.jetbrains.rider.model.RdVersionInfo
import com.jetbrains.rider.run.configurations.NET_TFM_REGEX
import org.jetbrains.annotations.ApiStatus

private val LOG = Logger.getInstance("#com.jetbrains.aspire.rider.util.TargetFrameworkUtils")

/**
 * Converts a raw MSBuild `TargetFramework` value into a [RdTargetFrameworkId].
 *
 * The platform grammar is reused from [NET_TFM_REGEX] so that platform-specific target frameworks
 * (`net10.0-windows`, `net8.0-windows10.0.19041.0`, `net9.0-android`) are parsed as well.
 *
 * The whole [targetFramework] value is kept as a presentable name, because it's passed back to the backend
 * and parsed there with `TargetFrameworkId.Create`.
 */
@ApiStatus.Internal
fun parseTargetFrameworkId(targetFramework: String): RdTargetFrameworkId? {
    val tfm = targetFramework.trim()

    val version = NET_TFM_REGEX.matchEntire(tfm)?.groups?.get("version")?.value?.trim('.')
    if (version.isNullOrEmpty()) {
        LOG.warn("Unable to parse target framework $targetFramework")
        return null
    }

    val versionParts = version.split('.').map { it.toIntOrNull() }
    if (versionParts.size > 3 || versionParts.any { it == null }) {
        LOG.warn("Unable to parse a version of the target framework $targetFramework")
        return null
    }

    val versionInfo = RdVersionInfo(
        versionParts[0]!!,
        versionParts.getOrNull(1) ?: 0,
        versionParts.getOrNull(2) ?: 0
    )

    return RdTargetFrameworkId(
        versionInfo,
        ".NETCoreApp",
        tfm,
        isNetCoreApp = true,
        isNetFramework = false
    )
}
