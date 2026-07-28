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
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertSame
import kotlin.test.assertTrue

class MonitoringWebSocketFactoryTest {
    @Test
    fun `preserves delegate send result and records it`() {
        val events = mutableListOf<WebSocketEvent>()
        val socket = FakeWebSocket(sendResult = false)
        val factory = MonitoringWebSocketFactory(
            delegate = FakeFactory(socket),
            recorder = { events += it },
            configuration = configuration(),
        )

        val monitored = factory.newWebSocket(request(), EmptyWebSocketListener)
        val result = monitored.send("hello")

        assertFalse(result)
        assertEquals(listOf("hello"), socket.sentText)
        val event = assertIs<WebSocketEvent.TextMessage>(events.last())
        assertEquals(Direction.OUTGOING, event.direction)
        assertEquals(false, event.accepted)
    }

    @Test
    fun `recorder failure never changes socket behavior`() {
        val socket = FakeWebSocket(sendResult = true)
        val factory = MonitoringWebSocketFactory(
            delegate = FakeFactory(socket),
            recorder = { error("recorder unavailable") },
            configuration = configuration(),
        )

        val monitored = factory.newWebSocket(request(), EmptyWebSocketListener)

        assertTrue(monitored.send("still sent"))
        assertEquals(listOf("still sent"), socket.sentText)
    }

    @Test
    fun `application listener runs before incoming event is recorded`() {
        val order = mutableListOf<String>()
        val socket = FakeWebSocket()
        val delegateFactory = FakeFactory(socket)
        val factory = MonitoringWebSocketFactory(
            delegate = delegateFactory,
            recorder = { event ->
                if (event is WebSocketEvent.TextMessage) order += "recorder"
            },
            configuration = configuration(),
        )
        val listener = object : WebSocketListener() {
            override fun onMessage(webSocket: WebSocket, text: String) {
                order += "application"
            }
        }

        factory.newWebSocket(request(), listener)
        delegateFactory.listener.onMessage(socket, "incoming")

        assertEquals(listOf("application", "recorder"), order)
    }

    @Test
    fun `application callback receives monitored socket`() {
        var callbackSocket: WebSocket? = null
        val realSocket = FakeWebSocket()
        val delegateFactory = FakeFactory(realSocket)
        val factory = MonitoringWebSocketFactory(
            delegateFactory,
            recorder = { },
            configuration = configuration(),
        )
        val listener = object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                callbackSocket = webSocket
            }
        }

        val returnedSocket = factory.newWebSocket(request(), listener)
        delegateFactory.listener.onOpen(realSocket, response())

        assertSame(returnedSocket, callbackSocket)
    }

    @Test
    fun `binary capture respects payload limit`() {
        val events = mutableListOf<WebSocketEvent>()
        val socket = FakeWebSocket()
        val factory = MonitoringWebSocketFactory(
            delegate = FakeFactory(socket),
            recorder = { events += it },
            configuration = configuration(maxBinaryPayloadBytes = 2),
        )

        factory.newWebSocket(request(), EmptyWebSocketListener)
            .send(ByteString.of(1, 2, 3, 4))

        val event = assertIs<WebSocketEvent.BinaryMessage>(events.last())
        assertEquals(4, event.originalSize)
        assertEquals(listOf<Byte>(1, 2), event.bytes.toList())
        assertTrue(event.truncated)
    }

    private fun configuration(maxBinaryPayloadBytes: Int = 256) =
        MonitoringWebSocketFactory.Configuration(
            maxBinaryPayloadBytes = maxBinaryPayloadBytes,
            sessionIdProvider = { "test-session" },
        )

    private fun request() = Request.Builder()
        .url("https://example.com/socket")
        .header("Authorization", "secret")
        .build()

    private fun response() = Response.Builder()
        .request(request())
        .protocol(Protocol.HTTP_1_1)
        .message("Switching Protocols")
        .code(101)
        .build()
}

private class FakeFactory(
    private val socket: WebSocket,
) : WebSocket.Factory {
    lateinit var listener: WebSocketListener

    override fun newWebSocket(request: Request, listener: WebSocketListener): WebSocket {
        this.listener = listener
        return socket
    }
}

private object EmptyWebSocketListener : WebSocketListener()

private class FakeWebSocket(
    private val sendResult: Boolean = true,
) : WebSocket {
    val sentText = mutableListOf<String>()

    override fun request(): Request = Request.Builder()
        .url("https://example.com/socket")
        .build()

    override fun queueSize(): Long = 0

    override fun send(text: String): Boolean {
        sentText += text
        return sendResult
    }

    override fun send(bytes: ByteString): Boolean = sendResult

    override fun close(code: Int, reason: String?): Boolean = true

    override fun cancel() = Unit
}
