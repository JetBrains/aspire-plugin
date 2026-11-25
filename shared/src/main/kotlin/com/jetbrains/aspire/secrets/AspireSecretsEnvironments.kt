package com.jetbrains.aspire.secrets

import com.intellij.openapi.util.io.OSAgnosticPathUtil
import com.intellij.platform.eel.EelOsFamily
import com.intellij.util.PathUtil
import org.jetbrains.annotations.ApiStatus
import java.nio.file.Path

/**
 * Rules for the environment names and the locations of the Aspire secrets files.
 *
 * They follow the Aspire CLI: an environment name must be a portable file name, and environments are compared
 * case-insensitively.
 */
@ApiStatus.Internal
object AspireSecretsEnvironments {
    /** The environment that Aspire uses when none is specified. */
    const val DEFAULT_ENVIRONMENT: String = "Development"

    private const val SECRETS_FILE_EXTENSION = ".json"
    private const val ASPIRE_DIRECTORY_NAME = ".aspire"
    private const val SECRETS_DIRECTORY_NAME = "secrets"
    private val ENVIRONMENT_NAME_PATTERN = Regex("[A-Za-z0-9][A-Za-z0-9_-]{0,79}")

    /**
     * Checks that [path] is an Aspire secrets file: `.aspire/secrets/<apphost-id>/<environment>.json`.
     */
    fun isSecretsFile(path: Path): Boolean {
        val fileName = path.fileName?.toString() ?: return false
        if (getEnvironment(fileName) == null) return false

        val secretsDirectory = path.parent?.parent ?: return false
        return secretsDirectory.fileName?.toString() == SECRETS_DIRECTORY_NAME &&
                secretsDirectory.parent?.fileName?.toString() == ASPIRE_DIRECTORY_NAME
    }

    fun getFileName(environment: String): String = environment + SECRETS_FILE_EXTENSION

    /**
     * Returns the environment of the secrets file [fileName], or `null` if it is not a secrets file.
     *
     * The environment must have 1–80 ASCII letters, digits, hyphens, or underscores and start with a letter or digit.
     *
     * The extension must be exactly [SECRETS_FILE_EXTENSION], because the CLI writes and reads only that spelling,
     * and [getFileName] has to produce the same name on case-sensitive file systems.
     */
    fun getEnvironment(fileName: String): String? {
        if (!fileName.endsWith(SECRETS_FILE_EXTENSION)) return null
        return fileName.dropLast(SECRETS_FILE_EXTENSION.length).takeIf(ENVIRONMENT_NAME_PATTERN::matches)
    }

    /**
     * Returns the path of the [environment] secrets file that `aspire secret path` printed,
     * or `null` if the [output] has no such path.
     *
     * The CLI can print notices before and after the path, so the path is the last line that is an absolute path
     * to the secrets file of the [environment].
     *
     * The CLI runs in the environment of the project and prints a path of that environment,
     * which can differ from the IDE OS (WSL, Docker), so the path is checked against the [osFamily] of the environment.
     */
    @Suppress("UnstableApiUsage")
    fun parseSecretsFilePath(output: String, environment: String, osFamily: EelOsFamily): String? {
        val fileName = getFileName(environment)
        return output
            .lineSequence()
            .map { it.trim() }
            .lastOrNull { isAbsolutePath(it, osFamily) && PathUtil.getFileName(it).equals(fileName, ignoreCase = true) }
    }

    /**
     * Checks that [path] is an absolute path of the [osFamily]: a path from the root on POSIX,
     * or a path from a drive letter on Windows.
     */
    @Suppress("UnstableApiUsage")
    private fun isAbsolutePath(path: String, osFamily: EelOsFamily): Boolean =
        when (osFamily) {
            EelOsFamily.Posix -> path.startsWith('/')
            EelOsFamily.Windows -> OSAgnosticPathUtil.isAbsoluteDosPath(path)
        }

    /**
     * Returns the [environments] without case-insensitive duplicates.
     * The default environment comes first, the others follow in alphabetical order.
     */
    fun sort(environments: Collection<String>): List<String> =
        environments
            .distinctBy { it.lowercase() }
            .sortedWith(
                compareBy<String> { !it.equals(DEFAULT_ENVIRONMENT, ignoreCase = true) }
                    .thenComparing(String.CASE_INSENSITIVE_ORDER)
            )
}
