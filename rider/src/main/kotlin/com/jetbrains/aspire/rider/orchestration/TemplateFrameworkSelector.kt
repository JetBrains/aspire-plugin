package com.jetbrains.aspire.rider.orchestration

import com.jetbrains.aspire.rider.util.parseTargetFrameworkId
import com.jetbrains.rider.model.RdVersionInfo
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
enum class TemplateFrameworkSelectionStrategy { Lowest, Highest }

private val versionComparator = compareBy<RdVersionInfo>({ it.major }, { it.minor })
private val nullableVersionComparator = nullsFirst(versionComparator)

/**
 * A template found by an ID prefix, along with its `Framework` choices.
 *
 * [version] is parsed from the template ID suffix (see [parseTemplateIdVersion]).
 */
@ApiStatus.Internal
data class TemplateCandidate(
    val id: String,
    val version: RdVersionInfo?,
    val frameworks: Set<String>,
)

/**
 * The result of [selectTemplate]: the ID of the selected template and the `Framework` choice to pass to it.
 */
@ApiStatus.Internal
data class TemplateSelection(
    val id: String,
    val framework: String,
)

/**
 * Parses the version suffix of [templateId] that follows [templateIdPrefix].
 *
 * For example, `MauiAspire.ServiceDefaults.CSharp.11.0` with the prefix `MauiAspire.ServiceDefaults.CSharp`
 * gives `11.0`. Returns `null` if there is no suffix or it isn't a version.
 */
@ApiStatus.Internal
fun parseTemplateIdVersion(templateId: String, templateIdPrefix: String): RdVersionInfo? {
    if (!templateId.startsWith("$templateIdPrefix.")) return null

    val parts = templateId.removePrefix("$templateIdPrefix.").split('.').map { it.toIntOrNull() ?: return null }
    if (parts.isEmpty() || parts.size > 3) return null

    return RdVersionInfo(parts[0], parts.getOrElse(1) { 0 }, parts.getOrElse(2) { 0 })
}

/**
 * Returns the candidate with the highest [TemplateCandidate.version]. Candidates without a version go last.
 */
@ApiStatus.Internal
fun selectLatestTemplate(candidates: List<TemplateCandidate>): TemplateCandidate? =
    candidates.maxWithOrNull(compareBy(nullableVersionComparator, TemplateCandidate::version))

/**
 * Selects a template and its `Framework` choice.
 *
 * The framework is selected by [selectTemplateFramework] from the choices of all [candidates].
 * Then the latest template (see [selectLatestTemplate]) that supports this framework is picked.
 *
 * Returns `null` if no framework can be selected.
 */
@ApiStatus.Internal
fun selectTemplate(
    candidates: List<TemplateCandidate>,
    targetFrameworks: List<String>,
    strategy: TemplateFrameworkSelectionStrategy,
): TemplateSelection? {
    val supportedFrameworks = candidates.flatMapTo(mutableSetOf()) { it.frameworks }
    val framework = selectTemplateFramework(targetFrameworks, supportedFrameworks, strategy) ?: return null
    val template = selectLatestTemplate(candidates.filter { framework in it.frameworks }) ?: return null

    return TemplateSelection(template.id, framework)
}

/**
 * Picks a .NET framework for the template.
 *
 * 1. Take the lowest or highest .NET version in [targetFrameworks], depending on [strategy].
 *    Compare only major and minor versions, so `net10.0-windows` counts as `net10.0`.
 * 2. Look for the matching `netX.Y` in [supportedFrameworks]. If it is missing, pick the newest
 *    .NET choice at or below that version, considering only choices without a platform suffix.
 * 3. If all eligible choices are newer, pick the oldest one.
 *
 * Returns `null` if there is no recognized .NET target version or no eligible template choice.
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
    val lowestLibraryVersion = parseNetCoreVersions(libraryTargetFrameworks)
        .minWithOrNull(versionComparator)
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
