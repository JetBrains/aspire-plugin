package com.jetbrains.aspire.unit.worker.dcp

import com.intellij.testFramework.TestApplicationManager
import com.jetbrains.aspire.dcp.AspireSessionServerEndpoint
import com.jetbrains.aspire.dcp.buildDcpEnvironmentVariables
import com.jetbrains.aspire.util.DEBUG_SESSION_INFO
import com.jetbrains.aspire.util.DEBUG_SESSION_RUN_MODE
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import kotlin.test.assertEquals

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
internal class DcpEnvironmentVariablesTest {
    @BeforeAll
    fun setUpApplication() {
        TestApplicationManager.getInstance()
    }

    @Test
    fun `environment includes serialized session server capabilities`() {
        val endpoint = AspireSessionServerEndpoint(12345, "test-token", isHttps = false, base64Cert = null)
        val expectedInfo = Json.parseToJsonElement(
            """
            {
                "protocols_supported": ["2024-04-23", "2025-10-01"],
                "supported_launch_configurations": ["project"]
            }
            """.trimIndent(),
        )

        val environment = buildDcpEnvironmentVariables(endpoint, debugExecutor = false)

        assertEquals(expectedInfo, Json.parseToJsonElement(environment.getValue(DEBUG_SESSION_INFO)))
    }

    @ParameterizedTest
    @CsvSource("true, Debug", "false, NoDebug")
    fun `environment uses the selected service run mode`(debug: Boolean, expectedMode: String) {
        val endpoint = AspireSessionServerEndpoint(12345, "test-token", isHttps = false, base64Cert = null)

        val environment = buildDcpEnvironmentVariables(endpoint, debug)

        assertEquals(expectedMode, environment[DEBUG_SESSION_RUN_MODE])
    }
}
