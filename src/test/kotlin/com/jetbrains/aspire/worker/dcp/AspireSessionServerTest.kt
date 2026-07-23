package com.jetbrains.aspire.worker.dcp

import com.jetbrains.aspire.generated.CreateProjectSessionRequest
import com.jetbrains.aspire.generated.CreateSessionResponse
import com.jetbrains.aspire.generated.DeleteSessionResponse
import com.jetbrains.aspire.generated.ErrorCode
import com.jetbrains.aspire.sessions.SessionLogReceived
import com.jetbrains.aspire.sessions.SessionProcessStarted
import org.testng.annotations.Test
import java.net.http.WebSocket
import java.net.http.WebSocketHandshakeException
import java.util.concurrent.ExecutionException
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class AspireSessionServerTest {
    companion object {
        private const val SECOND_HOST_ID = "vwxyz"
        private const val SECOND_INSTANCE_ID = "vwxyzGHIJK"
    }

    @Test
    fun `info returns 200`() {
        withServer(MockAspireSessionHost()) { baseUrl, _ ->
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
        withServer(MockAspireSessionHost()) { baseUrl, _ ->
            httpPut("$baseUrl/run_session", VALID_SESSION_BODY, token = null).assertStatus(401)
        }
    }

    @Test
    fun `run_session with wrong bearer is 401`() {
        withServer(MockAspireSessionHost()) { baseUrl, _ ->
            httpPut("$baseUrl/run_session", VALID_SESSION_BODY, token = "wrong-token").assertStatus(401)
        }
    }

    @Test
    fun `missing api-version is 400`() {
        withServer(MockAspireSessionHost()) { baseUrl, _ ->
            val response = httpPut("$baseUrl/run_session", VALID_SESSION_BODY, apiVersion = null)
            response.assertStatus(400)
            response.assertBodyContains("ProtocolVersionIsNotSupported")
        }
    }

    @Test
    fun `missing instance-id header is 400 InvalidAspireHostId`() {
        val host = MockAspireSessionHost()
        withServer(host) { baseUrl, _ ->
            val response = httpPut("$baseUrl/run_session", VALID_SESSION_BODY, instanceId = null)
            response.assertStatus(400)
            response.assertBodyContains("InvalidAspireHostId")
        }
    }

    @Test
    fun `malformed JSON body is 400`() {
        withServer(MockAspireSessionHost()) { baseUrl, _ ->
            httpPut("$baseUrl/run_session", "{ not json").assertStatus(400)
        }
    }

    @Test
    fun `no project launch configuration is 400`() {
        val host = MockAspireSessionHost()
        withServer(host) { baseUrl, _ ->
            val response = httpPut("$baseUrl/run_session", """{"launch_configurations":[]}""")
            response.assertStatus(400)
            response.assertBodyContains("UnableToFindSupportedLaunchConfiguration")
        }
    }

    @Test
    fun `create session returns 201`() {
        val host = MockAspireSessionHost().apply {
            onCreate = { CreateSessionResponse("session-1", null) }
        }

        withServer(host) { baseUrl, _ ->
            val body = """
                {"launch_configurations":[{"type":"project","project_path":"/p/App.csproj","mode":"Debug",
                "launch_profile":"https","disable_launch_profile":true}],
                "env":[{"name":"A","value":"1"}],"args":["--x"]}
            """.trimIndent()

            val response = httpPut("$baseUrl/run_session", body, instanceId = DEFAULT_INSTANCE_ID)

            response.assertStatus(201)
            assertEquals("/run_session/session-1", response.location())
            response.assertBodyContains("project_path")

            val request = host.lastCreateRequest as CreateProjectSessionRequest
            assertEquals("/p/App.csproj", request.projectPath)
            assertEquals("https", request.launchProfile)
            assertTrue(request.disableLaunchProfile)
            assertTrue(request.debug)
            assertEquals(DEFAULT_HOST_ID, request.dcpInstancePrefix)
            assertTrue(request.args.contentEquals(arrayOf("--x")))
            assertEquals(1, request.envs?.size)
            assertEquals("A", request.envs?.get(0)?.key)
            assertEquals("1", request.envs?.get(0)?.value)
        }
    }

    @Test
    fun `delete returns 200`() {
        val host = MockAspireSessionHost()
        withServer(host) { baseUrl, _ ->
            val response = httpDelete("$baseUrl/run_session/s1")

            response.assertStatus(200)

            val request = host.lastDeleteRequest
            assertEquals("s1", request?.sessionId)
            assertEquals(DEFAULT_HOST_ID, request?.dcpInstancePrefix)
        }
    }

    @Test
    fun `delete unknown session is 204`() {
        val host = MockAspireSessionHost().apply {
            onDelete = { DeleteSessionResponse(null, ErrorCode.AspireSessionNotFound) }
        }

        withServer(host) { baseUrl, _ ->
            httpDelete("$baseUrl/run_session/s1").assertStatus(204)
        }
    }

    @Test
    fun `notify upgrade without bearer is rejected`() {
        withServer(MockAspireSessionHost()) { baseUrl, _ ->
            val failure = assertFailsWith<ExecutionException> {
                connectNotify(baseUrl, TestWsListener(), token = null)
            }
            assertTrue(failure.cause is WebSocketHandshakeException, "cause was ${failure.cause}")
        }
    }

    @Test
    fun `notify upgrade for an unknown AppHost is rejected`() {
        withServer(MockAspireSessionHost()) { baseUrl, _ ->
            val failure = assertFailsWith<ExecutionException> {
                connectNotify(baseUrl, TestWsListener(), instanceId = SECOND_INSTANCE_ID)
            }
            assertTrue(failure.cause is WebSocketHandshakeException, "cause was ${failure.cause}")
        }
    }

    @Test
    fun `notify upgrade without an AppHost ID is rejected`() {
        withServer(MockAspireSessionHost()) { baseUrl, _ ->
            val failure = assertFailsWith<ExecutionException> {
                connectNotify(baseUrl, TestWsListener(), instanceId = null)
            }
            assertTrue(failure.cause is WebSocketHandshakeException, "cause was ${failure.cause}")
        }
    }

    @Test
    fun `notify emits processRestarted for SessionProcessStarted`() {
        val host = MockAspireSessionHost()
        withServer(host) { baseUrl, _ ->
            val listener = TestWsListener()
            val webSocket = connectNotify(baseUrl, listener)
            try {
                host.emit(SessionProcessStarted("s1", 4242L))

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
        val host = MockAspireSessionHost()
        withServer(host) { baseUrl, _ ->
            val listener = TestWsListener()
            val webSocket = connectNotify(baseUrl, listener)
            try {
                host.emit(SessionLogReceived("s1", true, "hello\n"))

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

    @Test
    fun `notify streams are isolated by AppHost`() {
        val host = MockAspireSessionHost().apply { addHost(SECOND_HOST_ID) }
        withServer(host) { baseUrl, _ ->
            val firstListener = TestWsListener()
            val secondListener = TestWsListener()
            val firstWebSocket = connectNotify(baseUrl, firstListener)
            val secondWebSocket = connectNotify(baseUrl, secondListener, instanceId = SECOND_INSTANCE_ID)
            try {
                host.emit(SessionProcessStarted("first-session", 101L))
                assertTrue(firstListener.nextFrame().contains("first-session"))
                assertTrue(!secondListener.hasFrame(), "Second AppHost received a first AppHost event")

                host.emit(SessionProcessStarted("second-session", 202L), SECOND_HOST_ID)
                assertTrue(secondListener.nextFrame().contains("second-session"))
                assertTrue(!firstListener.hasFrame(), "First AppHost received a second AppHost event")
            } finally {
                firstWebSocket.sendClose(WebSocket.NORMAL_CLOSURE, "done")
                secondWebSocket.sendClose(WebSocket.NORMAL_CLOSURE, "done")
            }
        }
    }

    @Test
    fun `reconnecting an AppHost replaces only its notify stream`() {
        val host = MockAspireSessionHost().apply { addHost(SECOND_HOST_ID) }
        withServer(host) { baseUrl, _ ->
            val firstAppHostListener = TestWsListener()
            val secondAppHostListener = TestWsListener()
            val replacementListener = TestWsListener()
            val firstAppHostWebSocket = connectNotify(baseUrl, firstAppHostListener)
            val secondAppHostWebSocket = connectNotify(baseUrl, secondAppHostListener, instanceId = SECOND_INSTANCE_ID)
            val replacementWebSocket = connectNotify(baseUrl, replacementListener)
            try {
                firstAppHostListener.nextClose()

                host.emit(SessionProcessStarted("replacement-session", 303L))
                host.emit(SessionProcessStarted("other-session", 404L), SECOND_HOST_ID)

                assertTrue(replacementListener.nextFrame().contains("replacement-session"))
                assertTrue(secondAppHostListener.nextFrame().contains("other-session"))
                assertTrue(!firstAppHostListener.hasFrame(), "Replaced stream received an event")
            } finally {
                firstAppHostWebSocket.abort()
                secondAppHostWebSocket.sendClose(WebSocket.NORMAL_CLOSURE, "done")
                replacementWebSocket.sendClose(WebSocket.NORMAL_CLOSURE, "done")
            }
        }
    }
}
