@file:Suppress("UnstableApiUsage")

package com.jetbrains.aspire.cli

import com.intellij.diagnostic.rethrowControlFlowException
import com.intellij.notification.Notification
import com.intellij.notification.NotificationType
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.text.StringUtil
import com.intellij.platform.eel.ReadResult
import com.intellij.platform.eel.channels.EelReceiveChannel
import com.intellij.platform.eel.path.EelPath
import com.intellij.platform.eel.provider.asEelPath
import com.intellij.platform.eel.provider.getEelDescriptor
import com.intellij.platform.eel.provider.toEelApi
import com.intellij.platform.eel.spawnProcess
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import com.intellij.util.text.SemVer
import com.jetbrains.aspire.AspireCoreBundle
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.jetbrains.annotations.ApiStatus
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.file.Path
import kotlin.time.Duration.Companion.seconds

@Service(Service.Level.PROJECT)
@ApiStatus.Internal
class AspireCliUpdateService(private val project: Project) {
    companion object {
        fun getInstance(project: Project): AspireCliUpdateService = project.service()

        private val LOG = logger<AspireCliUpdateService>()
        private val JSON = Json { ignoreUnknownKeys = true }
        private const val MAX_CAPTURE_BYTES = 1024 * 1024
        private val VERSION_PATTERN = Regex(
            "(0|[1-9]\\d{0,4})\\.(0|[1-9]\\d{0,4})\\.(0|[1-9]\\d{0,4})" +
                    "(?:-[0-9A-Za-z-]+(?:\\.[0-9A-Za-z-]+)*)?(?:\\+[0-9A-Za-z-]+(?:\\.[0-9A-Za-z-]+)*)?",
        )
    }

    private val mutex = Mutex()
    private val inspectedCliPaths = mutableSetOf<CliPath>()

    @RequiresBackgroundThread
    suspend fun checkIfCliOutdated() {
        val executablePath = AspireCliLocator.getInstance(project).locate() ?: return
        checkIfCliOutdated(executablePath)
    }

    @RequiresBackgroundThread
    suspend fun checkIfCliOutdated(executablePath: EelPath, executionDirectory: EelPath? = null): Unit =
        mutex.withLock {
            val resolvedDirectory = executionDirectory
                ?: project.basePath?.let { Path.of(it).asEelPath() }
                ?: executablePath.descriptor.toEelApi().userInfo.home
            val cliPath = CliPath(executablePath, resolvedDirectory)
            if (cliPath in inspectedCliPaths) {
                LOG.debug("Already checked CLI at $cliPath")
                return
            }

            // Doctor runs all environment checks, which have a two-minute budget. Its exit code can be
            // nonzero because of unrelated checks; only the structured cli-version metadata matters here.
            val releaseSuggestion = loadReleaseSuggestion(executablePath, resolvedDirectory)
            inspectedCliPaths.add(cliPath)

            if (releaseSuggestion != null && releaseSuggestion.availableRelease > releaseSuggestion.observedRelease) {
                displayUpdateNotification(
                    executablePath,
                    releaseSuggestion.observedRelease.rawVersion,
                    releaseSuggestion.availableRelease.rawVersion.substringBefore('+')
                )
            }
        }

    private suspend fun loadReleaseSuggestion(
        executablePath: EelPath,
        executionDirectory: EelPath
    ): ReleaseSuggestion? = withTimeoutOrNull(130.seconds) {
        val doctorArguments = listOf("doctor", "--format", "json", "--nologo")
        val diagnosticCommand = executeCliCommand(
            executablePath,
            executionDirectory,
            doctorArguments,
        ) ?: return@withTimeoutOrNull null

        val diagnosticReport = deserializeDiagnosticReport(diagnosticCommand.standardOutput)
        if (diagnosticReport?.validationResults != null)
            return@withTimeoutOrNull selectReleaseSuggestion(diagnosticReport)
        else
            return@withTimeoutOrNull null
    }

    private suspend fun executeCliCommand(
        executablePath: EelPath,
        executionDirectory: EelPath,
        commandArguments: List<String>,
    ): CommandOutput? = try {
        coroutineScope {
            val eelApi = project.getEelDescriptor().toEelApi()
            val cliProcess = eelApi.exec
                .spawnProcess(executablePath)
                .args(commandArguments)
                .env(mapOf("ASPIRE_NON_INTERACTIVE" to "true"))
                .workingDirectory(executionDirectory)
                .scope(this)
                .eelIt()
            try {
                cliProcess.stdin.close(null)
                val outputReader = async { cliProcess.stdout.readLimitedText(MAX_CAPTURE_BYTES) }
                val errorReader = async { cliProcess.stderr.readLimitedText(MAX_CAPTURE_BYTES) }
                cliProcess.exitCode.await()
                CommandOutput(outputReader.await(), errorReader.await())
            } finally {
                cliProcess.stop(commandArguments.first(), LOG)
            }
        }
    } catch (e: Exception) {
        rethrowControlFlowException(e)
        LOG.debug("Unable to check Aspire CLI updates at $executablePath", e)
        null
    }

    private fun deserializeDiagnosticReport(capturedText: String): DiagnosticReport? = try {
        JSON.decodeFromString<DiagnosticReport>(capturedText)
    } catch (_: IllegalArgumentException) {
        null
    }

    private fun selectReleaseSuggestion(diagnosticReport: DiagnosticReport): ReleaseSuggestion? {
        val releaseDetails =
            diagnosticReport.validationResults?.firstOrNull { it.identifier == "cli-version" }?.details ?: return null
        val observedRelease = releaseDetails.installedVersionText?.let(::parseSemanticVersion) ?: return null
        val installationChannel = releaseDetails.installationChannel?.lowercase()
        if (installationChannel !in setOf("stable", "daily", "staging")) return null
        if (!releaseDetails.lookupFailure.isNullOrEmpty()) return null
        val availableVersionText = releaseDetails.availableVersionText ?: return null
        val availableRelease = parseSemanticVersion(availableVersionText) ?: return null
        val publishedReleaseChannel = releaseDetails.availableReleaseChannel?.lowercase()
            ?: if (availableRelease.preRelease == null) "stable" else "prerelease"
        if (publishedReleaseChannel !in setOf("stable", "prerelease")) return null
        val requiredReleaseChannel = if (installationChannel == "stable") "stable" else "prerelease"

        return if (publishedReleaseChannel == requiredReleaseChannel)
            ReleaseSuggestion(observedRelease, availableRelease)
        else null
    }

    private fun parseSemanticVersion(versionText: String): SemVer? {
        val trimmedVersion = versionText.trim()
        return trimmedVersion
            .takeIf { it.length <= 128 && VERSION_PATTERN.matches(it) }
            ?.let(SemVer::parseFromText)
    }

    private suspend fun EelReceiveChannel.readLimitedText(maxBytes: Int): String {
        val accumulatedBytes = ByteArrayOutputStream()
        val readBuffer = ByteBuffer.allocate(8192)
        do {
            readBuffer.clear()
            val readStatus = receive(readBuffer)
            if (accumulatedBytes.size() + readBuffer.position() > maxBytes) {
                throw IOException("Aspire CLI output exceeded $maxBytes bytes")
            }
            accumulatedBytes.write(readBuffer.array(), 0, readBuffer.position())
        } while (readStatus != ReadResult.EOF)

        return accumulatedBytes.toString(Charsets.UTF_8)
    }

    private fun displayUpdateNotification(
        executablePath: EelPath,
        installedVersionText: String,
        availableVersionText: String
    ) {
        if (project.isDisposed) return

        Notification(
            "Aspire",
            AspireCoreBundle.message("notification.aspire.cli.outdated.title"),
            AspireCoreBundle.message(
                "notification.aspire.cli.outdated.content",
                installedVersionText,
                StringUtil.escapeXmlEntities(executablePath.toString()),
                availableVersionText,
            ),
            NotificationType.WARNING,
        ).notify(project)
    }

    private data class CliPath(val executablePath: EelPath, val executionDirectory: EelPath)

    private data class ReleaseSuggestion(
        val observedRelease: SemVer,
        val availableRelease: SemVer,
    )

    private data class CommandOutput(val standardOutput: String, val standardError: String)

    @Serializable
    private data class DiagnosticReport(
        @SerialName("checks") val validationResults: List<DiagnosticCheck>? = null
    )

    @Serializable
    private data class DiagnosticCheck(
        @SerialName("name") val identifier: String? = null,
        @SerialName("metadata") val details: CliReleaseDetails? = null,
    )

    @Serializable
    private data class CliReleaseDetails(
        @SerialName("currentVersion") val installedVersionText: String? = null,
        @SerialName("latestVersion") val availableVersionText: String? = null,
        @SerialName("identityChannel") val installationChannel: String? = null,
        @SerialName("latestVersionChannel") val availableReleaseChannel: String? = null,
        @SerialName("updateCheckError") val lookupFailure: String? = null,
    )
}
