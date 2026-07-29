package com.jetbrains.aspire.worker

import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
data class AppHostLogEntry(val text: String, val isStdErr: Boolean)
