package com.jetbrains.aspire.secrets

import com.jetbrains.aspire.worker.AspireAppHostId
import com.jetbrains.aspire.worker.AspirePath
import kotlinx.serialization.Serializable
import org.jetbrains.annotations.ApiStatus

/**
 * Locates the Aspire secrets of an AppHost and opens its secrets files.
 *
 * Aspire stores AppHost secrets in one `<environment>.json` file per environment, in a directory
 * that the Aspire CLI resolves for each AppHost (`aspire secret path`).
 *
 * See: https://aspire.dev/reference/cli/commands/aspire-secret-path/
 */
@ApiStatus.Internal
interface AspireSecretsService {
    /**
     * Resolves the secrets directory of the AppHost and lists the environments that already have a secrets file.
     *
     * Returns `null` if the secrets directory cannot be resolved (for example, the Aspire CLI is not found);
     * in that case the user has already been notified.
     */
    suspend fun getSecretsStore(appHostId: AspireAppHostId): AspireSecretsStore?

    /**
     * Opens the secrets file of the [environment] from the [store] in an editor.
     *
     * If the file no longer exists, the user is notified instead.
     */
    suspend fun openSecretsFile(store: AspireSecretsStore, environment: String)
}

/**
 * The Aspire secrets of an AppHost.
 *
 * @property directory the directory that contains the `<environment>.json` secrets files
 * @property environments the environments that have a secrets file, with the spelling of the file names
 */
@ApiStatus.Internal
@Serializable
data class AspireSecretsStore(
    val directory: AspirePath,
    val environments: List<String>,
)
