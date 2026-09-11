package com.jetbrains.aspire.services

import org.jetbrains.annotations.ApiStatus
import javax.swing.JComponent

@ApiStatus.Internal
sealed interface AppHostUiState {
    data object Initial : AppHostUiState

    data class Active(
        val dashboardUrl: String?,
        val consoleComponent: JComponent
    ) : AppHostUiState

    data class Inactive(
        val consoleComponent: JComponent
    ) : AppHostUiState
}
