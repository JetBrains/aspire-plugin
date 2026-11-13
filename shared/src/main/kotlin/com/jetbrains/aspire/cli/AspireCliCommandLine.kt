package com.jetbrains.aspire.cli

import com.jetbrains.aspire.worker.AspirePath
import org.jetbrains.annotations.ApiStatus

/**
 * A command that manages the Aspire CLI itself (for example, installs or updates it) and runs in a terminal.
 */
@ApiStatus.Internal
data class AspireCliCommandLine(
    val command: List<String>,
    val workingDirectory: AspirePath,
)
