package com.jetbrains.aspire.cli

import com.jetbrains.aspire.worker.AspirePath
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
interface AspireCliInstallationService {
    suspend fun prepareCommand(): AspireCliInstallationCommandLine?
}

@ApiStatus.Internal
data class AspireCliInstallationCommandLine(
    val command: List<String>,
    val workingDirectory: AspirePath,
)
