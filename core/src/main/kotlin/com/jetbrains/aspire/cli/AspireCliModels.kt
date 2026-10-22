package com.jetbrains.aspire.cli

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import org.jetbrains.annotations.ApiStatus

/**
 * JSON resource model matching Aspire's [ResourceJson](https://github.com/microsoft/aspire/blob/main/src/Shared/Model/Serialization/ResourceJson.cs#L15).
 */
@ApiStatus.Internal
@Serializable
data class AspireCliResource(
    val name: String? = null,
    val displayName: String? = null,
    val resourceType: String? = null,
    val uid: String? = null,
    val state: String? = null,
    val waitingFor: List<String>? = null,
    val stateStyle: String? = null,
    val creationTimestamp: String? = null,
    val startTimestamp: String? = null,
    val stopTimestamp: String? = null,
    val source: String? = null,
    val exitCode: Int? = null,
    val healthStatus: String? = null,
    val dashboardUrl: String? = null,
    val relationships: List<AspireCliResourceRelationship>? = null,
    val urls: List<AspireCliResourceUrl>? = null,
    val volumes: List<AspireCliResourceVolume>? = null,
    val properties: Map<String, JsonElement?>? = null,
    val environment: Map<String, String?>? = null,
    val healthReports: Map<String, AspireCliResourceHealthReport>? = null,
    val commands: Map<String, AspireCliResourceCommand>? = null,
)

/**
 * Matches Aspire's [ResourceUrlJson](https://github.com/microsoft/aspire/blob/main/src/Shared/Model/Serialization/ResourceJson.cs#L130).
 */
@ApiStatus.Internal
@Serializable
data class AspireCliResourceUrl(
    val name: String? = null,
    val displayName: String? = null,
    val url: String? = null,
    val isInternal: Boolean = false,
)

/**
 * Matches Aspire's [ResourceVolumeJson](https://github.com/microsoft/aspire/blob/main/src/Shared/Model/Serialization/ResourceJson.cs#L157).
 */
@ApiStatus.Internal
@Serializable
data class AspireCliResourceVolume(
    val source: String? = null,
    val target: String? = null,
    val mountType: String? = null,
    val isReadOnly: Boolean = false,
)

/**
 * Matches Aspire's [ResourceHealthReportJson](https://github.com/microsoft/aspire/blob/main/src/Shared/Model/Serialization/ResourceJson.cs#L184).
 */
@ApiStatus.Internal
@Serializable
data class AspireCliResourceHealthReport(
    val status: String? = null,
    val description: String? = null,
    val exceptionMessage: String? = null,
)

/**
 * Matches Aspire's [ResourceRelationshipJson](https://github.com/microsoft/aspire/blob/main/src/Shared/Model/Serialization/ResourceJson.cs#L205).
 */
@ApiStatus.Internal
@Serializable
data class AspireCliResourceRelationship(
    val type: String? = null,
    val resourceName: String? = null,
)

/**
 * Matches Aspire's [ResourceCommandJson](https://github.com/microsoft/aspire/blob/main/src/Shared/Model/Serialization/ResourceJson.cs#L221).
 */
@ApiStatus.Internal
@Serializable
data class AspireCliResourceCommand(
    val displayName: String? = null,
    val description: String? = null,
    val visibility: String? = null,
    val state: String? = null,
    val sortOrder: Int? = null,
    val argumentInputs: List<AspireCliResourceCommandArgumentInput>? = null,
)

/**
 * Matches Aspire's [ResourceCommandArgumentJson](https://github.com/microsoft/aspire/blob/main/src/Shared/Model/Serialization/ResourceJson.cs#L273).
 */
@ApiStatus.Internal
@Serializable
data class AspireCliResourceCommandArgumentInput(
    val name: String,
    val label: String? = null,
    val description: String? = null,
    val enableDescriptionMarkdown: Boolean = false,
    val inputType: String,
    val required: Boolean = false,
    val placeholder: String? = null,
    val value: String? = null,
    val options: Map<String, String?>? = null,
    val allowCustomChoice: Boolean = false,
    val disabled: Boolean = false,
    val maxLength: Int? = null,
    val dynamicLoading: AspireCliResourceCommandArgumentDynamicLoading? = null,
)

/**
 * Matches Aspire's [ResourceCommandArgumentDynamicLoadingJson](https://github.com/microsoft/aspire/blob/main/src/Shared/Model/Serialization/ResourceJson.cs#L351).
 */
@ApiStatus.Internal
@Serializable
data class AspireCliResourceCommandArgumentDynamicLoading(
    val alwaysLoadOnStart: Boolean = false,
    val dependsOnInputs: List<String>? = null,
)

/**
 * Matches Aspire's [LogLineJson](https://github.com/microsoft/aspire/blob/main/src/Aspire.Cli/Commands/LogsCommand.cs#L25).
 */
@ApiStatus.Internal
@Serializable
data class AspireCliLogLine(
    val resourceName: String,
    val timestamp: String? = null,
    val content: String,
    val isError: Boolean,
)
