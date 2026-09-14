package com.jetbrains.aspire.services

import javax.swing.JComponent

internal sealed interface AppHostUiState {
    data object Initial : AppHostUiState

    data class Active(
        val dashboardUrl: String?,
        val consoleComponent: JComponent
    ) : AppHostUiState

    data class Inactive(
        val consoleComponent: JComponent
    ) : AppHostUiState
}
