package com.jetbrains.aspire.rider.orchestration

import com.jetbrains.aspire.rider.util.parseTargetFrameworkId
import com.jetbrains.rider.model.RdVersionInfo
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
enum class TemplateFrameworkSelectionStrategy { Lowest, Highest }

private val versionComparator = compareBy<RdVersionInfo>({ it.major }, { it.minor })

/**
 * Selects a .NET framework from [supportedFrameworks] based on [targetFrameworks].
 *
 * [TemplateFrameworkSelectionStrategy.Lowest] uses the lowest .NET target version as the upper limit.
 * [TemplateFrameworkSelectionStrategy.Highest] uses the highest. The comparison uses major and minor versions,
 * so a target such as `net10.0-windows` counts as `net10.0`.
 *
 * Among supported .NET frameworks without a platform suffix, picks the highest version at or below the limit.
 * If all eligible versions are above the limit, picks the lowest one.
 *
 * For targets `net9.0` and `net10.0-windows` and choices `net8.0` and `net10.0`, `Lowest` picks `net8.0`
 * and `Highest` picks `net10.0`.
 *
 * Returns `null` if there are no recognized .NET target versions or no eligible supported frameworks.
 */
@ApiStatus.Internal
fun selectTemplateFramework(
    targetFrameworks: List<String>,
    supportedFrameworks: Set<String>,
    strategy: TemplateFrameworkSelectionStrategy,
): String? {
    if (supportedFrameworks.isEmpty()) return null

    val targetFrameworkVersions = parseNetCoreVersions(targetFrameworks)
    val selectedVersion = when (strategy) {
        TemplateFrameworkSelectionStrategy.Lowest -> targetFrameworkVersions.minWithOrNull(versionComparator)
        TemplateFrameworkSelectionStrategy.Highest -> targetFrameworkVersions.maxWithOrNull(versionComparator)
    } ?: return null

    val normalizedFramework = "net${selectedVersion.major}.${selectedVersion.minor}"
    if (normalizedFramework in supportedFrameworks) return normalizedFramework

    val supportedVersions = supportedFrameworks.mapNotNull { framework ->
        if ('-' in framework) return@mapNotNull null
        parseNetCoreVersion(framework)?.let { framework to it }
    }
    val supportedVersionComparator = compareBy(versionComparator, Pair<String, RdVersionInfo>::second)

    val highestAtOrBelow = supportedVersions
        .asSequence()
        .filter { (_, version) -> versionComparator.compare(version, selectedVersion) <= 0 }
        .maxWithOrNull(supportedVersionComparator)

    return (highestAtOrBelow ?: supportedVersions.minWithOrNull(supportedVersionComparator))?.first
}

/**
 * Checks whether a project with [projectTargetFrameworks] can reference a library with [libraryTargetFrameworks].
 *
 * Every .NET project framework must be greater than or equal to the lowest .NET library framework.
 * Returns `true` if the frameworks of either side are unknown.
 */
@ApiStatus.Internal
fun isCompatibleWithLibraryFrameworks(
    projectTargetFrameworks: List<String>,
    libraryTargetFrameworks: List<String>,
): Boolean {
    val lowestLibraryVersion = parseNetCoreVersions(libraryTargetFrameworks).minWithOrNull(versionComparator)
        ?: return true

    return parseNetCoreVersions(projectTargetFrameworks).all {
        versionComparator.compare(it, lowestLibraryVersion) >= 0
    }
}

private fun parseNetCoreVersions(targetFrameworks: List<String>): List<RdVersionInfo> =
    targetFrameworks.mapNotNull(::parseNetCoreVersion)

private fun parseNetCoreVersion(targetFramework: String): RdVersionInfo? {
    if (isLegacyNetFramework(targetFramework)) return null
    return parseTargetFrameworkId(targetFramework)?.version
}

private fun isLegacyNetFramework(targetFramework: String): Boolean =
    targetFramework.trim().matches(Regex("""net\d{2,3}""", RegexOption.IGNORE_CASE))
