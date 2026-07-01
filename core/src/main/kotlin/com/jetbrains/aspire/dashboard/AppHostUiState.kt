package com.jetbrains.aspire.dashboard

import org.jetbrains.annotations.ApiStatus
import javax.swing.JComponent

@ApiStatus.Internal
sealed interface AppHostUiState {
    val dashboardUrl: String?

    data class Active(
        override val dashboardUrl: String?,
        val consoleComponent: JComponent
    ) : AppHostUiState

    data class Inactive(
        override val dashboardUrl: String?
    ) : AppHostUiState
}
