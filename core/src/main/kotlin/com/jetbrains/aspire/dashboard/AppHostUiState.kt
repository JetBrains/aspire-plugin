package com.jetbrains.aspire.dashboard

import com.intellij.execution.ui.ConsoleView
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
sealed interface AppHostUiState {
    val dashboardUrl: String?

    data class Active(
        override val dashboardUrl: String?,
        val consoleView: ConsoleView
    ) : AppHostUiState

    data class Inactive(
        override val dashboardUrl: String?
    ) : AppHostUiState
}