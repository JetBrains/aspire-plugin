package com.jetbrains.aspire.cli

import com.jetbrains.aspire.worker.AspirePath
import kotlinx.serialization.Serializable
import org.jetbrains.annotations.ApiStatus

/**
 * A prepared [AspireCliCommand] that is ready to run in a terminal.
 */
@ApiStatus.Internal
@Serializable
data class AspireCliCommandLine(
    val command: List<String>,
    val workingDirectory: AspirePath,
)
