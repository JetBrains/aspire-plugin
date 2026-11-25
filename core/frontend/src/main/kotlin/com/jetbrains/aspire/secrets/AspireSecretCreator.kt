@file:Suppress("UnstableApiUsage")

package com.jetbrains.aspire.secrets

import com.intellij.openapi.application.EDT
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.InputValidatorEx
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.util.NlsContexts
import com.intellij.platform.ide.progress.withBackgroundProgress
import com.jetbrains.aspire.AspireCoreFrontendBundle
import com.jetbrains.aspire.cli.AspireCliCommand
import com.jetbrains.aspire.cli.AspireCliCommandRunner
import com.jetbrains.aspire.worker.AspireAppHostData
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Adds an Aspire secret to an AppHost with `aspire secret set` and opens the secrets file that it writes.
 *
 * The plugin asks only for the secret key; the Aspire CLI asks for the value in the terminal,
 * so the value never passes through the IDE.
 *
 * See: https://aspire.dev/reference/cli/commands/aspire-secret-set/
 */
@Service(Service.Level.PROJECT)
internal class AspireSecretCreator(private val project: Project, private val cs: CoroutineScope) {
    companion object {
        fun getInstance(project: Project): AspireSecretCreator = project.service()
    }

    fun createSecret(appHostData: AspireAppHostData) {
        cs.launch {
            val key = askSecretKey(appHostData) ?: return@launch

            val environment = AspireSecretsEnvironments.DEFAULT_ENVIRONMENT
            val command = AspireCliCommand.SetSecret(appHostData.id, key, environment)
            val tabName = AspireCoreFrontendBundle.message("secrets.set.terminal.title", appHostData.name)
            AspireCliCommandRunner
                .getInstance(project)
                .run(command, tabName) {
                    openSecretsFile(appHostData, environment)
                }
        }
    }

    private suspend fun askSecretKey(appHostData: AspireAppHostData): String? = withContext(Dispatchers.EDT) {
        Messages.showInputDialog(
            project,
            AspireCoreFrontendBundle.message("secrets.set.key.dialog.message", appHostData.name),
            AspireCoreFrontendBundle.message("secrets.set.key.dialog.title"),
            null,
            null,
            SecretKeyValidator,
        )?.trim()
    }

    private suspend fun openSecretsFile(appHostData: AspireAppHostData, environment: String) {
        val secretsService = project.service<AspireSecretsService>()
        val store = withBackgroundProgress(
            project,
            AspireCoreFrontendBundle.message("secrets.progress.title", appHostData.name)
        ) {
            secretsService.getSecretsStore(appHostData.id)
        } ?: return

        val storedEnvironment = store.environments.firstOrNull { it.equals(environment, ignoreCase = true) } ?: return
        secretsService.openSecretsFile(store, storedEnvironment)
    }

    private object SecretKeyValidator : InputValidatorEx {
        override fun getErrorText(inputString: String): @NlsContexts.DetailedDescription String? {
            val key = inputString.trim()
            return when {
                key.isEmpty() -> AspireCoreFrontendBundle.message("secrets.set.key.dialog.empty.error")
                // The Aspire CLI would read such a key as an option.
                key.startsWith("-") -> AspireCoreFrontendBundle.message("secrets.set.key.dialog.option.error")
                // The Aspire CLI would read such a key as a response file.
                key.startsWith("@") -> AspireCoreFrontendBundle.message("secrets.set.key.dialog.response.file.error")
                else -> null
            }
        }
    }
}
