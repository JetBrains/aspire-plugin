package com.jetbrains.aspire.cli

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
@Serializable
data class AspireCliResource(
    val name: String,
    val displayName: String? = null,
    val resourceType: String,
    val source: String? = null,
    val state: String? = null,
    val stateStyle: String? = null,
    val healthStatus: String? = null,
    val healthReports: Map<String, AspireCliResourceHealthReport>? = null,
    val exitCode: Int? = null,
    val dashboardUrl: String? = null,
    val urls: List<AspireCliResourceUrl>? = null,
    val commands: Map<String, AspireCliResourceCommand>? = null,
    val properties: Map<String, JsonElement>? = null,
)

@ApiStatus.Internal
@Serializable
data class AspireCliResourceUrl(
    val name: String? = null,
    val displayName: String? = null,
    val url: String,
    val isInternal: Boolean = false,
)

@ApiStatus.Internal
@Serializable
data class AspireCliResourceHealthReport(
    val status: String? = null,
    val description: String? = null,
    val exceptionMessage: String? = null,
)

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

@ApiStatus.Internal
@Serializable
data class AspireCliResourceCommandArgumentInput(
    val name: String,
    val label: String? = null,
    val description: String? = null,
    val enableDescriptionMarkdown: Boolean? = null,
    val inputType: AspireCliResourceCommandInputType,
    val required: Boolean? = null,
    val placeholder: String? = null,
    val value: String? = null,
    val options: Map<String, String?>? = null,
    val allowCustomChoice: Boolean? = null,
    val disabled: Boolean? = null,
    val maxLength: Int? = null,
    val dynamicLoading: AspireCliResourceCommandArgumentDynamicLoading? = null,
)

@ApiStatus.Internal
@Serializable
enum class AspireCliResourceCommandInputType {
    Text, SecretText, Choice, Boolean, Number
}

@ApiStatus.Internal
@Serializable
data class AspireCliResourceCommandArgumentDynamicLoading(
    val alwaysLoadOnStart: Boolean? = null,
    val dependsOnInputs: List<String>? = null,
)

@ApiStatus.Internal
@Serializable
data class AspireCliLogLine(
    val resourceName: String,
    val timestamp: String? = null,
    val content: String,
    val isError: Boolean,
)
