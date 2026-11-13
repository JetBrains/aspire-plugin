package com.jetbrains.aspire.cli

import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
interface AspireCliInstallationService {
    suspend fun prepareCommand(): AspireCliCommandLine?
}
