package com.jetbrains.aspire.cli

import com.jetbrains.aspire.worker.AspireAppHostId
import kotlinx.serialization.Serializable
import org.jetbrains.annotations.ApiStatus

/**
 * An Aspire command that runs in a terminal.
 *
 * Use [AspireCliCommandService] to resolve a command into an [AspireCliCommandLine].
 */
@ApiStatus.Internal
@Serializable
sealed interface AspireCliCommand {
    /** Installs the Aspire CLI with the official installation script. */
    @Serializable
    data object Install : AspireCliCommand

    /** Updates the Aspire CLI itself (`aspire update --self`). */
    @Serializable
    data object SelfUpdate : AspireCliCommand

    /** A command that targets a specific AppHost (`--apphost`). */
    @Serializable
    sealed interface AppHostCommand : AspireCliCommand {
        val appHostId: AspireAppHostId
    }

    /** `aspire publish --apphost <path>` */
    @Serializable
    data class Publish(override val appHostId: AspireAppHostId) : AppHostCommand

    /** `aspire deploy --apphost <path>` */
    @Serializable
    data class Deploy(override val appHostId: AspireAppHostId) : AppHostCommand
}

@ApiStatus.Internal
interface AspireCliCommandService {
    /**
     * Resolves [command] into a command line that can be run in a terminal.
     *
     * Returns `null` if the command cannot be prepared (for example, the Aspire CLI or the AppHost is not found);
     * in that case the user has already been notified.
     */
    suspend fun prepare(command: AspireCliCommand): AspireCliCommandLine?
}
