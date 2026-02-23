package com.jetbrains.aspire.dashboard

import com.jetbrains.aspire.worker.AspireResourceData
import org.jetbrains.annotations.ApiStatus
import javax.swing.JComponent

@ApiStatus.Internal
data class ResourceUiState(
    val resourceData: AspireResourceData,
    val consoleComponent: JComponent
)
