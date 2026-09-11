package com.jetbrains.aspire.worker

import kotlinx.serialization.Serializable
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
@Serializable
data class AppHostLogEntry(val text: String, val isStdErr: Boolean)
