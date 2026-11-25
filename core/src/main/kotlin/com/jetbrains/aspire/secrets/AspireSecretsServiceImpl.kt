@file:Suppress("UnstableApiUsage")

package com.jetbrains.aspire.secrets

import com.intellij.diagnostic.rethrowControlFlowException
import com.intellij.notification.Notification
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.diagnostic.debug
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.FileEditorOpenRequest
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.platform.eel.path.EelPath
import com.intellij.platform.eel.provider.asEelPath
import com.intellij.platform.eel.provider.asNioPath
import com.intellij.platform.eel.provider.getEelDescriptor
import com.intellij.platform.eel.provider.toEelApi
import com.intellij.platform.eel.spawnProcess
import com.jetbrains.aspire.AspireCoreBundle
import com.jetbrains.aspire.cli.AspireCliInvalidPathException
import com.jetbrains.aspire.cli.AspireCliLocator
import com.jetbrains.aspire.cli.AspireCliNotifications
import com.jetbrains.aspire.cli.readLimitedText
import com.jetbrains.aspire.cli.stop
import com.jetbrains.aspire.worker.AspireAppHostId
import com.jetbrains.aspire.worker.AspirePath
import com.jetbrains.aspire.worker.AspireWorker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.nio.file.Path
import kotlin.io.path.isDirectory
import kotlin.io.path.isRegularFile
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.name
import kotlin.time.Duration.Companion.seconds

/**
 * Resolves the secrets directory of an AppHost with `aspire secret path` and opens its secrets files.
 *
 * The Aspire CLI derives the directory from the `UserSecretsId` of the AppHost, or from a hash of the AppHost path
 * if the AppHost has none, so the plugin doesn't compute it itself.
 */
internal class AspireSecretsServiceImpl(private val project: Project) : AspireSecretsService {
    companion object {
        private val LOG = logger<AspireSecretsServiceImpl>()

        private const val MAX_CAPTURE_BYTES = 64 * 1024
        private const val MAX_ERROR_LENGTH = 1000
        private val COMMAND_TIMEOUT = 60.seconds
    }

    override suspend fun getSecretsStore(appHostId: AspireAppHostId): AspireSecretsStore? =
        withContext(Dispatchers.IO) {
            try {
                resolveSecretsStore(appHostId)
            } catch (e: AspireCliInvalidPathException) {
                LOG.info("Unable to resolve Aspire secrets: ${e.message}")
                AspireCliNotifications.notifyInvalidCliPath(project, e)
                null
            } catch (e: Exception) {
                rethrowControlFlowException(e)
                LOG.warn("Unable to resolve Aspire secrets of the AppHost $appHostId", e)
                notifyResolutionFailed(e.message.orEmpty(), suggestCliUpdate = false)
                null
            }
        }

    override suspend fun openSecretsFile(store: AspireSecretsStore, environment: String) {
        val directory = EelPath.parse(store.directory.value, project.getEelDescriptor()).asNioPath()
        val file = directory.resolve(AspireSecretsEnvironments.getFileName(environment))
        val virtualFile = withContext(Dispatchers.IO) {
            VirtualFileManager.getInstance().refreshAndFindFileByNioPath(file)
        }
        if (virtualFile == null) {
            LOG.info("Unable to find the Aspire secrets file $file")
            notifySecretsFileNotFound(file)
            return
        }

        FileEditorManager
            .getInstance(project)
            .openFile(virtualFile, FileEditorOpenRequest.withFocus(true))
    }

    private suspend fun resolveSecretsStore(appHostId: AspireAppHostId): AspireSecretsStore? {
        val appHost = AspireWorker.getInstance(project).getAppHostById(appHostId)
        if (appHost == null || !appHost.appHostPath.isRegularFile()) {
            Notification(
                "Aspire",
                AspireCoreBundle.message("notification.aspire.deployment.app.host.not.found.title"),
                AspireCoreBundle.message("notification.aspire.deployment.app.host.not.found.content"),
                NotificationType.WARNING,
            ).notify(project)
            return null
        }

        val cliPath = AspireCliLocator.getInstance(project).locate()
        if (cliPath == null) {
            AspireCliNotifications.notifyCliNotFound(project)
            return null
        }

        val appHostPath = appHost.appHostPath.toAbsolutePath()
        val secretsFilePath = findDefaultSecretsFile(cliPath, appHostPath) ?: return null
        val directory = secretsFilePath.parent ?: return null
        val environments = findEnvironments(directory.asNioPath())

        return AspireSecretsStore(AspirePath(directory.toString()), environments)
    }

    private suspend fun findDefaultSecretsFile(cliPath: EelPath, appHostPath: Path): EelPath? {
        val arguments = listOf(
            "secret",
            "path",
            "--apphost", appHostPath.asEelPath().toString(),
            "--environment", AspireSecretsEnvironments.DEFAULT_ENVIRONMENT,
            "--non-interactive",
            "--nologo",
        )
        val output = withTimeoutOrNull(COMMAND_TIMEOUT) {
            runCli(cliPath, appHostPath.parent.asEelPath(), arguments)
        }
        if (output == null) {
            notifyResolutionFailed(
                AspireCoreBundle.message("notification.aspire.secrets.path.timeout"),
                suggestCliUpdate = false
            )
            return null
        }

        val filePath =
            if (output.exitCode == 0) AspireSecretsEnvironments.parseSecretsFilePath(
                output.standardOutput,
                AspireSecretsEnvironments.DEFAULT_ENVIRONMENT
            )
            else null
        if (filePath == null) {
            LOG.debug { "aspire secret path exited with ${output.exitCode}: ${output.standardOutput} ${output.standardError}" }
            val error = output.standardError
                .ifBlank { output.standardOutput }
                .trim()
                .takeLast(MAX_ERROR_LENGTH)
            // An older CLI rejects the command or its options and exits with a non-zero code.
            notifyResolutionFailed(error, suggestCliUpdate = output.exitCode != 0)
            return null
        }

        return EelPath.parse(filePath, project.getEelDescriptor())
    }

    private suspend fun runCli(cliPath: EelPath, workingDirectory: EelPath, arguments: List<String>) = coroutineScope {
        val eelApi = project.getEelDescriptor().toEelApi()
        val process = eelApi.exec
            .spawnProcess(cliPath)
            .args(arguments)
            .env(mapOf("ASPIRE_NON_INTERACTIVE" to "true"))
            .workingDirectory(workingDirectory)
            .scope(this)
            .eelIt()
        try {
            process.stdin.close(null)
            val outputReader = async { process.stdout.readLimitedText(MAX_CAPTURE_BYTES) }
            val errorReader = async { process.stderr.readLimitedText(MAX_CAPTURE_BYTES) }
            val exitCode = process.exitCode.await()
            CommandOutput(exitCode, outputReader.await(), errorReader.await())
        } finally {
            process.stop(arguments.take(2).joinToString(" "), LOG)
        }
    }

    private fun findEnvironments(directory: Path): List<String> {
        if (!directory.isDirectory()) return emptyList()

        return directory
            .listDirectoryEntries()
            .filter { it.isRegularFile() }
            .mapNotNull { AspireSecretsEnvironments.getEnvironment(it.name) }
    }

    private fun notifyResolutionFailed(error: String, suggestCliUpdate: Boolean) {
        val contentKey =
            if (suggestCliUpdate) "notification.aspire.secrets.path.failed.cli.version.content"
            else "notification.aspire.secrets.path.failed.content"
        val notification = Notification(
            "Aspire",
            AspireCoreBundle.message("notification.aspire.secrets.path.failed.title"),
            AspireCoreBundle.message(contentKey, StringUtil.escapeXmlEntities(error)),
            NotificationType.ERROR,
        )
        if (suggestCliUpdate) {
            notification.addAction(ActionManager.getInstance().getAction("Aspire.UpdateCli"))
        }
        notification.notify(project)
    }

    private fun notifySecretsFileNotFound(file: Path) {
        Notification(
            "Aspire",
            AspireCoreBundle.message("notification.aspire.secrets.file.not.found.title"),
            AspireCoreBundle.message(
                "notification.aspire.secrets.file.not.found.content",
                StringUtil.escapeXmlEntities(file.toString())
            ),
            NotificationType.WARNING,
        ).notify(project)
    }

    private data class CommandOutput(val exitCode: Int, val standardOutput: String, val standardError: String)
}
