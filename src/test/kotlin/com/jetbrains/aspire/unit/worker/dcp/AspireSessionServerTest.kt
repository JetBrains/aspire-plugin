package com.jetbrains.aspire.unit.worker.dcp

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.TestApplicationManager
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.replaceService
import com.jetbrains.aspire.sessions.CreateSessionResponse
import com.jetbrains.aspire.sessions.DeleteSessionResponse
import com.jetbrains.aspire.sessions.DotNetSessionLaunchConfiguration
import com.jetbrains.aspire.sessions.ErrorCode
import com.jetbrains.aspire.sessions.SessionLogReceived
import com.jetbrains.aspire.sessions.SessionProcessStarted
import com.jetbrains.aspire.settings.AspireSettings
import com.jetbrains.aspire.worker.dcp.AspireSessionServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import java.net.http.WebSocket
import java.net.http.WebSocketHandshakeException
import java.util.concurrent.ExecutionException
import kotlin.io.path.Path
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
internal class AspireSessionServerTest {
    private lateinit var testRootDisposable: Disposable

    @BeforeAll
    fun setUpApplication() {
        TestApplicationManager.getInstance()
    }

    @BeforeEach
    fun setUpSettings() {
        testRootDisposable = Disposer.newDisposable("AspireSessionServerTest")
        val settings = AspireSettings().apply { connectToDcpViaHttps = false }
        ApplicationManager.getApplication().replaceService(AspireSettings::class.java, settings, testRootDisposable)
    }

    @AfterEach
    fun tearDown() {
        Disposer.dispose(testRootDisposable)
    }

    @Test
    fun `each server generates its own token`() = timeoutRunBlocking {
        val project = ProjectManager.getInstance().defaultProject
        val firstSessionManager = MockSessionManager()
        val secondSessionManager = MockSessionManager()

        val firstServer = AspireSessionServer(firstSessionManager, project)
        val secondServer = AspireSessionServer(secondSessionManager, project)

        try {
            firstServer.start()
            secondServer.start()

            val firstEndpoint = checkNotNull(firstServer.endpoint)
            val secondEndpoint = checkNotNull(secondServer.endpoint)
            assertTrue(firstEndpoint.token.isNotBlank())
            assertTrue(secondEndpoint.token.isNotBlank())
            assertNotEquals(firstEndpoint.token, secondEndpoint.token)
        } finally {
            firstServer.stop()
            secondServer.stop()
        }
    }

    @Test
    fun `endpoint is null before start`() {
        val project = ProjectManager.getInstance().defaultProject
        val sessionManager = MockSessionManager()
        val server = AspireSessionServer(sessionManager, project)

        val endpoint = server.endpoint

        assertNull(endpoint)
    }

    @Test
    fun `stop clears its endpoint`() {
        val sessionManager = MockSessionManager()
        withServer(sessionManager) { _, server ->
            server.stop()

            assertNull(server.endpoint)
        }
    }

    @Test
    fun `server can restart after stop`() {
        val sessionManager = MockSessionManager()
        withServer(sessionManager) { _, server ->
            val firstEndpoint = checkNotNull(server.endpoint)

            server.stop()
            server.start()
            val secondEndpoint = checkNotNull(server.endpoint)

            assertTrue(secondEndpoint.port > 0)
            assertEquals(firstEndpoint.token, secondEndpoint.token)
        }
    }

    @Test
    fun `start reuses its endpoint`() {
        val sessionManager = MockSessionManager()
        withServer(sessionManager) { _, server ->
            val endpoint = checkNotNull(server.endpoint)

            server.start()

            assertTrue(endpoint.port > 0)
            assertFalse(endpoint.isHttps)
            assertNull(endpoint.base64Cert)
            assertEquals(endpoint, server.endpoint)
        }
    }

    @Test
    fun `info returns 200`() {
        withServer(MockSessionManager()) { baseUrl, _ ->
            val response = httpGet("$baseUrl/info")
            response.assertStatus(200)
            response.assertBodyContains("protocols_supported")
            response.assertBodyContains("2024-04-23")
            response.assertBodyContains("2025-10-01")
            response.assertBodyContains("supported_launch_configurations")
            response.assertBodyContains("project")
        }
    }

    @Test
    fun `run_session without bearer is 401`() {
        withServer(MockSessionManager()) { baseUrl, _ ->
            httpPut("$baseUrl/run_session", VALID_SESSION_BODY, token = null).assertStatus(401)
        }
    }

    @Test
    fun `run_session with wrong bearer is 401`() {
        withServer(MockSessionManager()) { baseUrl, _ ->
            httpPut("$baseUrl/run_session", VALID_SESSION_BODY, token = "wrong-token").assertStatus(401)
        }
    }

    @Test
    fun `missing api-version is 400`() {
        val sessionManager = MockSessionManager()
        withServer(sessionManager) { baseUrl, server ->
            val response = httpPut("$baseUrl/run_session", VALID_SESSION_BODY, checkNotNull(server.endpoint).token, apiVersion = null)

            response.assertStatus(400)
            response.assertBodyContains("ProtocolVersionIsNotSupported")
        }
    }

    @Test
    fun `missing instance-id header is 400 InvalidAspireHostId`() {
        val sessionManager = MockSessionManager()
        withServer(sessionManager) { baseUrl, server ->
            val response = httpPut("$baseUrl/run_session", VALID_SESSION_BODY, checkNotNull(server.endpoint).token, instanceId = null)

            response.assertStatus(400)
            response.assertBodyContains("InvalidAspireHostId")
        }
    }

    @Test
    fun `malformed JSON body is 400`() {
        val sessionManager = MockSessionManager()
        withServer(sessionManager) { baseUrl, server ->
            val response = httpPut("$baseUrl/run_session", "{ not json", checkNotNull(server.endpoint).token)

            response.assertStatus(400)
        }
    }

    @Test
    fun `no project launch configuration is 400`() {
        val sessionManager = MockSessionManager()
        withServer(sessionManager) { baseUrl, server ->
            val response = httpPut("$baseUrl/run_session", """{"launch_configurations":[]}""", checkNotNull(server.endpoint).token)

            response.assertStatus(400)
            response.assertBodyContains("UnableToFindSupportedLaunchConfiguration")
        }
    }

    @Test
    fun `create session client error returns 400`() {
        val sessionManager = MockSessionManager().apply {
            onCreate = { CreateSessionResponse(null, ErrorCode.UnsupportedLaunchConfigurationType) }
        }

        withServer(sessionManager) { baseUrl, server ->
            val response = httpPut("$baseUrl/run_session", VALID_SESSION_BODY, checkNotNull(server.endpoint).token)

            response.assertStatus(400)
            response.assertBodyContains("UnsupportedLaunchConfigurationType")
            assertNull(response.location())
        }
    }

    @Test
    fun `create session returns 201`() {
        val sessionManager = MockSessionManager().apply {
            onCreate = { CreateSessionResponse("session-1", null) }
        }
        val expectedProjectPath = Path("/p/App.csproj")
        val body = """
            {"launch_configurations":[{"type":"project","project_path":"/p/App.csproj","mode":"Debug",
            "launch_profile":"https","disable_launch_profile":true}],
            "env":[{"name":"A","value":"1"}],"args":["--x"]}
        """.trimIndent()

        withServer(sessionManager) { baseUrl, server ->
            val response = httpPut("$baseUrl/run_session", body, checkNotNull(server.endpoint).token, instanceId = DEFAULT_INSTANCE_ID)

            response.assertStatus(201)
            assertEquals("/run_session/session-1", response.location())
            response.assertBodyContains("project_path")

            val request = checkNotNull(sessionManager.lastCreateRequest)
            val launchConfiguration = assertIs<DotNetSessionLaunchConfiguration>(request.launchConfiguration)
            assertEquals(expectedProjectPath, launchConfiguration.projectPath)
            assertEquals("https", launchConfiguration.launchProfile)
            assertTrue(launchConfiguration.disableLaunchProfile)
            assertTrue(launchConfiguration.debug)
            assertEquals(DEFAULT_HOST_ID, request.dcpInstancePrefix)
            assertEquals(listOf("--x"), launchConfiguration.args)
            assertEquals(listOf("A" to "1"), launchConfiguration.envs)
        }
    }

    @Test
    fun `create session preserves defaults and filters null environment values`() {
        val sessionManager = MockSessionManager()
        val body = """
            {"launch_configurations":[{"type":"project","project_path":"/p/App.csproj"}],
            "env":[{"name":"unset"},{"name":"empty","value":""}]}
        """.trimIndent()

        withServer(sessionManager) { baseUrl, server ->
            val response = httpPut("$baseUrl/run_session", body, checkNotNull(server.endpoint).token)

            response.assertStatus(201)
            val request = checkNotNull(sessionManager.lastCreateRequest)
            val launchConfiguration = assertIs<DotNetSessionLaunchConfiguration>(request.launchConfiguration)
            assertFalse(launchConfiguration.debug)
            assertFalse(launchConfiguration.disableLaunchProfile)
            assertNull(launchConfiguration.launchProfile)
            assertNull(launchConfiguration.args)
            assertEquals(listOf("empty" to ""), launchConfiguration.envs)
        }
    }

    @Test
    fun `create session preserves absent arguments and environment`() {
        val sessionManager = MockSessionManager()

        withServer(sessionManager) { baseUrl, server ->
            val response = httpPut("$baseUrl/run_session", VALID_SESSION_BODY, checkNotNull(server.endpoint).token)

            response.assertStatus(201)
            val request = checkNotNull(sessionManager.lastCreateRequest)
            val launchConfiguration = assertIs<DotNetSessionLaunchConfiguration>(request.launchConfiguration)
            assertNull(launchConfiguration.args)
            assertNull(launchConfiguration.envs)
        }
    }

    @Test
    fun `delete returns 200`() {
        val sessionManager = MockSessionManager()
        withServer(sessionManager) { baseUrl, server ->
            val response = httpDelete("$baseUrl/run_session/s1", checkNotNull(server.endpoint).token)

            response.assertStatus(200)

            val request = sessionManager.lastDeleteRequest
            assertEquals("s1", request?.sessionId)
            assertEquals(DEFAULT_HOST_ID, request?.dcpInstancePrefix)
        }
    }

    @Test
    fun `delete unknown session is 204`() {
        val sessionManager = MockSessionManager().apply {
            onDelete = { DeleteSessionResponse(null, ErrorCode.AspireSessionNotFound) }
        }

        withServer(sessionManager) { baseUrl, server ->
            val response = httpDelete("$baseUrl/run_session/s1", checkNotNull(server.endpoint).token)

            response.assertStatus(204)
        }
    }

    @Test
    fun `delete session server error returns 500`() {
        val sessionManager = MockSessionManager().apply {
            onDelete = { DeleteSessionResponse(null, ErrorCode.Unexpected) }
        }

        withServer(sessionManager) { baseUrl, server ->
            val response = httpDelete("$baseUrl/run_session/s1", checkNotNull(server.endpoint).token)

            response.assertStatus(500)
            response.assertBodyContains("UnexpectedError")
        }
    }

    @Test
    fun `notify upgrade without bearer is rejected`() {
        withServer(MockSessionManager()) { baseUrl, _ ->
            val failure = assertFailsWith<ExecutionException> {
                connectNotify(baseUrl, TestWsListener(), token = null)
            }
            assertTrue(failure.cause is WebSocketHandshakeException, "cause was ${failure.cause}")
        }
    }

    @Test
    fun `notify emits processRestarted for SessionProcessStarted`() {
        val sessionManager = MockSessionManager()
        withServer(sessionManager) { baseUrl, server ->
            val listener = TestWsListener()
            val webSocket = connectNotify(baseUrl, listener, checkNotNull(server.endpoint).token)
            try {
                sessionManager.emit(SessionProcessStarted("s1", 4242L))

                val frame = listener.nextFrame()

                assertTrue(frame.contains(""""session_id":"s1""""), frame)
                assertTrue(frame.contains(""""notification_type":"processRestarted""""), frame)
                assertTrue(frame.contains(""""pid":4242"""), frame)
            } finally {
                webSocket.sendClose(WebSocket.NORMAL_CLOSURE, "done")
            }
        }
    }

    @Test
    fun `notify emits serviceLogs and trims trailing newline`() {
        val sessionManager = MockSessionManager()
        withServer(sessionManager) { baseUrl, server ->
            val listener = TestWsListener()
            val webSocket = connectNotify(baseUrl, listener, checkNotNull(server.endpoint).token)
            try {
                sessionManager.emit(SessionLogReceived("s1", true, "hello\n"))

                val frame = listener.nextFrame()

                assertTrue(frame.contains(""""notification_type":"serviceLogs""""), frame)
                assertTrue(frame.contains(""""is_std_err":true"""), frame)
                assertTrue(frame.contains(""""log_message":"hello""""), frame)
                assertTrue(!frame.contains("""hello\n"""), "Trailing newline should be trimmed: $frame")
            } finally {
                webSocket.sendClose(WebSocket.NORMAL_CLOSURE, "done")
            }
        }
    }
}
