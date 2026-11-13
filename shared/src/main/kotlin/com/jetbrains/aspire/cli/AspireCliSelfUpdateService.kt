package com.jetbrains.aspire.cli

import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
interface AspireCliSelfUpdateService {
    suspend fun prepareCommand(): AspireCliCommandLine?
}
