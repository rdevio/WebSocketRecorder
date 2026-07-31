package io.github.rdevio.websocketrecorder.okhttp

import io.github.rdevio.websocketrecorder.core.Direction
import io.github.rdevio.websocketrecorder.core.WebSocketEvent
import okhttp3.Request
import okhttp3.WebSocket
import okio.ByteString
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertSame
import kotlin.test.assertTrue

class MonitoringWebSocketTest {
    @Test
    fun `records an accepted outgoing text message`() {
        val events = mutableListOf<WebSocketEvent>()
        val original = FakeOutgoingWebSocket(sendResult = true)
        val monitored = original.withMonitoring(
            recorder = { events += it },
            configuration = configuration(),
        )

        assertTrue(monitored.send("hello"))

        val event = assertIs<WebSocketEvent.TextMessage>(events.single())
        assertEquals(Direction.OUTGOING, event.direction)
        assertEquals("hello", event.text)
        assertEquals(true, event.accepted)
        assertEquals(listOf("hello"), original.sentTexts)
    }

    @Test
    fun `does not record a rejected outgoing message`() {
        val events = mutableListOf<WebSocketEvent>()
        val monitored = FakeOutgoingWebSocket(sendResult = false).withMonitoring(
            recorder = { events += it },
            configuration = configuration(),
        )

        assertFalse(monitored.send("rejected"))
        assertTrue(events.isEmpty())
    }

    @Test
    fun `recorder failure does not change send result`() {
        val monitored = FakeOutgoingWebSocket(sendResult = true).withMonitoring(
            recorder = { error("recorder unavailable") },
            configuration = configuration(),
        )

        assertTrue(monitored.send("still sent"))
    }

    @Test
    fun `delegates socket ownership operations unchanged`() {
        val original = FakeOutgoingWebSocket(sendResult = true)
        val monitored = original.withMonitoring(
            recorder = { },
            configuration = configuration(),
        )

        assertSame(original.request(), monitored.request())
        assertEquals(7, monitored.queueSize())
        assertTrue(monitored.close(1000, "done"))
        monitored.cancel()
        assertTrue(original.cancelled)
    }

    private fun configuration() = MonitoringWebSocket.Configuration(
        sessionIdProvider = { "outgoing-test" },
    )
}

private class FakeOutgoingWebSocket(
    private val sendResult: Boolean,
) : WebSocket {
    private val socketRequest = Request.Builder()
        .url("https://example.com/socket")
        .build()

    val sentTexts = mutableListOf<String>()
    var cancelled = false

    override fun request(): Request = socketRequest
    override fun queueSize(): Long = 7

    override fun send(text: String): Boolean {
        sentTexts += text
        return sendResult
    }

    override fun send(bytes: ByteString): Boolean = sendResult
    override fun close(code: Int, reason: String?): Boolean = true

    override fun cancel() {
        cancelled = true
    }
}
