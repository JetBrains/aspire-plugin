package com.jetbrains.aspire.unit.secrets

import com.jetbrains.aspire.secrets.AspireSecretsEnvironments
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import java.nio.file.Path
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

internal class AspireSecretsEnvironmentsTest {
    @ParameterizedTest
    @ValueSource(strings = ["Development", "production", "staging-eu", "test_1", "1st"])
    fun `secrets files with portable names have an environment`(environment: String) {
        val fileName = "$environment.json"

        val result = AspireSecretsEnvironments.getEnvironment(fileName)

        assertEquals(environment, result)
    }

    @ParameterizedTest
    @ValueSource(strings = ["-prod", "_prod", "prod env", "prod.eu", "Développement"])
    fun `secrets files with non-portable names have no environment`(environment: String) {
        val fileName = "$environment.json"

        val result = AspireSecretsEnvironments.getEnvironment(fileName)

        assertNull(result)
    }

    @Test
    fun `secrets files with names longer than 80 characters have no environment`() {
        val fileName = "a".repeat(81) + ".json"

        val result = AspireSecretsEnvironments.getEnvironment(fileName)

        assertNull(result)
    }

    @Test
    fun `secrets file name is the environment with the json extension`() {
        val fileName = AspireSecretsEnvironments.getFileName("Production")

        assertEquals("Production.json", fileName)
    }

    @Test
    fun `environment of a secrets file keeps the spelling of the file name`() {
        val environment = AspireSecretsEnvironments.getEnvironment("staging.json")

        assertEquals("staging", environment)
    }

    @ParameterizedTest
    @ValueSource(strings = ["Development.txt", "Development", ".json", "prod.eu.json", "Development.JSON", "Development.Json"])
    fun `files that are not secrets files have no environment`(fileName: String) {
        val environment = AspireSecretsEnvironments.getEnvironment(fileName)

        assertNull(environment)
    }

    @Test
    fun `environment file in the apphost directory of the aspire secrets is a secrets file`() {
        val path = Path.of("/home/user/.aspire/secrets/my-app-1234/Production.json")

        val isSecretsFile = AspireSecretsEnvironments.isSecretsFile(path)

        assertTrue(isSecretsFile)
    }

    @ParameterizedTest
    @ValueSource(
        strings = [
            "/home/user/.aspire/secrets/my-app-1234/notes.txt",
            "/home/user/.aspire/secrets/my-app-1234/prod.eu.json",
            "/home/user/.aspire/secrets/Production.json",
            "/home/user/.aspire/settings/my-app-1234/Production.json",
            "/home/user/aspire/secrets/my-app-1234/Production.json",
            "/home/user/project/config/Production.json",
            "/Production.json",
        ]
    )
    fun `other files are not secrets files`(file: String) {
        val path = Path.of(file)

        val isSecretsFile = AspireSecretsEnvironments.isSecretsFile(path)

        assertFalse(isSecretsFile)
    }

    @Test
    fun `default environment comes before the other environments`() {
        val environments = listOf("staging", "Production", "development")

        val result = AspireSecretsEnvironments.sort(environments)

        assertEquals(listOf("development", "Production", "staging"), result)
    }

    @Test
    fun `default environment is not added if it has no secrets file`() {
        val environments = listOf("Staging", "Production")

        val result = AspireSecretsEnvironments.sort(environments)

        assertEquals(listOf("Production", "Staging"), result)
    }

    @Test
    fun `environments that differ only in case are listed once`() {
        val environments = listOf("Production", "production")

        val result = AspireSecretsEnvironments.sort(environments)

        assertEquals(listOf("Production"), result)
    }

    @Test
    fun `secrets file path is the printed line without surrounding whitespace`() {
        val output = "  /home/user/.aspire/secrets/apphost-1234/Development.json  \n"

        val path = AspireSecretsEnvironments.parseSecretsFilePath(output, "Development")

        assertEquals("/home/user/.aspire/secrets/apphost-1234/Development.json", path)
    }

    @Test
    fun `secrets file path is found after a notice`() {
        val output = "Welcome to Aspire!\r\nC:\\Users\\user\\.aspire\\secrets\\my-app\\Development.json\r\n\r\n"

        val path = AspireSecretsEnvironments.parseSecretsFilePath(output, "Development")

        assertEquals("C:\\Users\\user\\.aspire\\secrets\\my-app\\Development.json", path)
    }

    @Test
    fun `secrets file path is found before a notice`() {
        val output = "/home/user/.aspire/secrets/my-app/Development.json\nA new version of Aspire CLI is available.\n"

        val path = AspireSecretsEnvironments.parseSecretsFilePath(output, "Development")

        assertEquals("/home/user/.aspire/secrets/my-app/Development.json", path)
    }

    @Test
    fun `secrets file path can be a UNC path`() {
        val output = "\\\\wsl.localhost\\Ubuntu\\home\\user\\.aspire\\secrets\\my-app\\Development.json\n"

        val path = AspireSecretsEnvironments.parseSecretsFilePath(output, "Development")

        assertEquals("\\\\wsl.localhost\\Ubuntu\\home\\user\\.aspire\\secrets\\my-app\\Development.json", path)
    }

    @Test
    fun `secrets file path of another environment is ignored`() {
        val output = "/home/user/.aspire/secrets/my-app/Production.json\n"

        val path = AspireSecretsEnvironments.parseSecretsFilePath(output, "Development")

        assertNull(path)
    }

    @Test
    fun `relative secrets file path is ignored`() {
        val output = "See Development.json\n"

        val path = AspireSecretsEnvironments.parseSecretsFilePath(output, "Development")

        assertNull(path)
    }

    @Test
    fun `blank output has no secrets file path`() {
        val output = " \n\n"

        val path = AspireSecretsEnvironments.parseSecretsFilePath(output, "Development")

        assertNull(path)
    }
}
