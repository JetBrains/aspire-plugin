package com.jetbrains.aspire.unit.worker.dcp

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.TestApplicationManager
import com.intellij.testFramework.ExtensionTestUtil
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.replaceService
import com.jetbrains.aspire.sessions.AzureFunctionsSessionLaunchConfiguration
import com.jetbrains.aspire.sessions.CreateSessionResponse
import com.jetbrains.aspire.sessions.DeleteSessionResponse
import com.jetbrains.aspire.sessions.DotNetSessionLaunchConfiguration
import com.jetbrains.aspire.sessions.ErrorCode
import com.jetbrains.aspire.sessions.ExternalBuildSessionLaunchConfiguration
import com.jetbrains.aspire.sessions.SessionLogReceived
import com.jetbrains.aspire.sessions.SessionProcessStarted
import com.jetbrains.aspire.settings.AspireSettings
import com.jetbrains.aspire.dcp.AspireSessionServer
import com.jetbrains.aspire.extensions.StartSessionRequestHandler
import com.jetbrains.aspire.sessions.StartSessionRequest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
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

    private class TestStartSessionRequestHandler(override val sessionType: String) : StartSessionRequestHandler {
        override fun isApplicable(request: StartSessionRequest): Boolean = false

        override suspend fun handleRequests(requests: List<StartSessionRequest>, project: Project) {
            error("This handler only supplies session type information")
        }
    }

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
    fun `info advertises registered session types without duplicates`() {
        val projectHandler = TestStartSessionRequestHandler("project")
        val customHandler = TestStartSessionRequestHandler("custom")
        val duplicateHandler = TestStartSessionRequestHandler("custom")
        val handlers = listOf(projectHandler, customHandler, duplicateHandler)
        val extensionPoint = ExtensionPointName<StartSessionRequestHandler>("com.jetbrains.aspire.startSessionRequestHandler")
        ExtensionTestUtil.maskExtensions(extensionPoint, handlers, testRootDisposable)
        val sessionManager = MockSessionManager()

        withServer(sessionManager) { baseUrl, _ ->
            val response = httpGet("$baseUrl/info")

            response.assertStatus(200)
            val info = Json.parseToJsonElement(response.body())
            val sessionTypes = info.jsonObject.getValue("supported_launch_configurations").jsonArray
                .map { it.jsonPrimitive.content }
            assertEquals(listOf("project", "custom"), sessionTypes)
        }
    }

    @Test
    fun `info advertises no session types when no handlers are registered`() {
        val extensionPoint = ExtensionPointName<StartSessionRequestHandler>("com.jetbrains.aspire.startSessionRequestHandler")
        ExtensionTestUtil.maskExtensions(extensionPoint, emptyList(), testRootDisposable)
        val sessionManager = MockSessionManager()
        withServer(sessionManager) { baseUrl, _ ->
            val response = httpGet("$baseUrl/info")

            response.assertStatus(200)
            val info = Json.parseToJsonElement(response.body()).jsonObject
            assertTrue(info.getValue("supported_launch_configurations").jsonArray.isEmpty())
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
    fun `create session maps azure-functions launch configuration`() {
        val sessionManager = MockSessionManager()
        val expectedProjectPath = Path("/p/Func.csproj")
        val body = """
            {"launch_configurations":[{"type":"azure-functions","project_path":"/p/Func.csproj","mode":"Debug"}],
            "env":[{"name":"A","value":"1"}],"args":["--x"]}
        """.trimIndent()

        withServer(sessionManager) { baseUrl, server ->
            val response = httpPut("$baseUrl/run_session", body, checkNotNull(server.endpoint).token)

            response.assertStatus(201)
            val request = checkNotNull(sessionManager.lastCreateRequest)
            val launchConfiguration = assertIs<AzureFunctionsSessionLaunchConfiguration>(request.launchConfiguration)
            assertEquals(expectedProjectPath, launchConfiguration.projectPath)
            assertTrue(launchConfiguration.debug)
            assertEquals(listOf("--x"), launchConfiguration.args)
            assertEquals(listOf("A" to "1"), launchConfiguration.envs)
        }
    }

    @Test
    fun `create session maps project with an external build`() {
        val sessionManager = MockSessionManager()
        val projectPath = Path("/p/App.csproj")
        val buildDirectory = Path("/sdk-root")
        val buildEnvironment = mapOf("BUILD_FLAVOR" to "custom")
        val runtimeEnvironment = listOf("BUILD_FLAVOR" to "runtime")
        val body = """
            {"launch_configurations":[{"type":"project-with-external-build.v1","project_path":"/p/App.csproj","mode":"Debug",
            "launch_profile":"https","disable_launch_profile":true,"build_configuration":"Release",
            "build_environment":{"BUILD_FLAVOR":"custom"},"build_working_directory":"/sdk-root","suppress_build":true}],
            "env":[{"name":"BUILD_FLAVOR","value":"runtime"}],"args":[]}
        """.trimIndent()

        withServer(sessionManager) { baseUrl, server ->
            val response = httpPut("$baseUrl/run_session", body, checkNotNull(server.endpoint).token)

            response.assertStatus(201)
            val request = checkNotNull(sessionManager.lastCreateRequest)
            val launchConfiguration = assertIs<ExternalBuildSessionLaunchConfiguration>(request.launchConfiguration)
            assertEquals(projectPath, launchConfiguration.projectPath)
            assertTrue(launchConfiguration.debug)
            assertEquals("https", launchConfiguration.launchProfile)
            assertTrue(launchConfiguration.disableLaunchProfile)
            assertEquals("Release", launchConfiguration.buildConfiguration)
            assertEquals(buildEnvironment, launchConfiguration.buildEnvironment)
            assertEquals(buildDirectory, launchConfiguration.buildWorkingDirectory)
            assertTrue(launchConfiguration.suppressBuild)
            assertEquals(emptyList(), launchConfiguration.args)
            assertEquals(runtimeEnvironment, launchConfiguration.envs)
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
