package com.jetbrains.aspire.services

import com.jetbrains.aspire.worker.AspireResourceData
import javax.swing.JComponent

internal data class ResourceUiState(
    val resourceData: AspireResourceData,
    val consoleComponent: JComponent
)
