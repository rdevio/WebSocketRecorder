package io.github.websocketrecorder.okhttp

import io.github.websocketrecorder.core.Direction
import io.github.websocketrecorder.core.WebSocketEvent
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertSame
import kotlin.test.assertTrue

class MonitoringWebSocketListenerTest {
    @Test
    fun `forwards the exact original WebSocket instance`() {
        val socket = FakeWebSocket()
        var applicationSocket: WebSocket? = null
        val listener = MonitoringWebSocketListener(
            delegate = object : WebSocketListener() {
                override fun onOpen(webSocket: WebSocket, response: Response) {
                    applicationSocket = webSocket
                }
            },
            recorder = { },
            configuration = configuration(),
        )

        listener.onOpen(socket, response())

        assertSame(socket, applicationSocket)
    }

    @Test
    fun `application listener runs before monitoring`() {
        val order = mutableListOf<String>()
        val listener = MonitoringWebSocketListener(
            delegate = object : WebSocketListener() {
                override fun onMessage(webSocket: WebSocket, text: String) {
                    order += "application"
                }
            },
            recorder = { order += "recorder" },
            configuration = configuration(),
        )

        listener.onMessage(FakeWebSocket(), "incoming")

        assertEquals(listOf("application", "recorder"), order)
    }

    @Test
    fun `recorder failure does not escape callback`() {
        var applicationCalled = false
        val listener = MonitoringWebSocketListener(
            delegate = object : WebSocketListener() {
                override fun onMessage(webSocket: WebSocket, text: String) {
                    applicationCalled = true
                }
            },
            recorder = { error("recorder unavailable") },
            configuration = configuration(),
        )

        listener.onMessage(FakeWebSocket(), "still delivered")

        assertTrue(applicationCalled)
    }

    @Test
    fun `records incoming text only as incoming`() {
        val events = mutableListOf<WebSocketEvent>()
        val listener = MonitoringWebSocketListener(
            delegate = EmptyWebSocketListener,
            recorder = { events += it },
            configuration = configuration(),
        )

        listener.onMessage(FakeWebSocket(), "hello")

        val event = assertIs<WebSocketEvent.TextMessage>(events.single())
        assertEquals(Direction.INCOMING, event.direction)
        assertEquals("hello", event.text)
        assertEquals(null, event.accepted)
    }

    @Test
    fun `binary capture respects payload limit`() {
        val events = mutableListOf<WebSocketEvent>()
        val listener = MonitoringWebSocketListener(
            delegate = EmptyWebSocketListener,
            recorder = { events += it },
            configuration = configuration(maxBinaryPayloadBytes = 2),
        )

        listener.onMessage(FakeWebSocket(), ByteString.of(1, 2, 3, 4))

        val event = assertIs<WebSocketEvent.BinaryMessage>(events.single())
        assertEquals(4, event.originalSize)
        assertEquals(listOf<Byte>(1, 2), event.bytes.toList())
        assertTrue(event.truncated)
    }

    private fun configuration(maxBinaryPayloadBytes: Int = 256) =
        MonitoringWebSocketListener.Configuration(
            maxBinaryPayloadBytes = maxBinaryPayloadBytes,
            sessionIdProvider = { "test-session" },
        )

    private fun request() = Request.Builder()
        .url("https://example.com/socket")
        .build()

    private fun response() = Response.Builder()
        .request(request())
        .protocol(Protocol.HTTP_1_1)
        .message("Switching Protocols")
        .code(101)
        .build()
}

private object EmptyWebSocketListener : WebSocketListener()

private class FakeWebSocket : WebSocket {
    override fun request(): Request = Request.Builder()
        .url("https://example.com/socket")
        .build()

    override fun queueSize(): Long = 0
    override fun send(text: String): Boolean = true
    override fun send(bytes: ByteString): Boolean = true
    override fun close(code: Int, reason: String?): Boolean = true
    override fun cancel() = Unit
}
