package io.github.websocketrecorder.okhttp

import io.github.websocketrecorder.core.CloseOrigin
import io.github.websocketrecorder.core.Direction
import io.github.websocketrecorder.core.WebSocketEvent
import io.github.websocketrecorder.core.WebSocketRecorder
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import java.util.UUID
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference

/**
 * A transparent [WebSocket.Factory] decorator that observes lifecycle and messages.
 *
 * The delegate factory remains responsible for connection settings, threading and transport.
 * Monitoring never changes return values and recorder failures are always suppressed.
 */
class MonitoringWebSocketFactory(
    private val delegate: WebSocket.Factory,
    private val recorder: WebSocketRecorder,
    private val configuration: Configuration = Configuration(),
) : WebSocket.Factory {

    override fun newWebSocket(request: Request, listener: WebSocketListener): WebSocket {
        val session = SessionRecorder(
            id = configuration.sessionIdProvider(),
            recorder = recorder,
            maxBinaryPayloadBytes = configuration.maxBinaryPayloadBytes,
        )
        val exposedSocket = AtomicReference<WebSocket>()
        val monitoringListener = MonitoringWebSocketListener(
            delegate = listener,
            session = session,
            exposedSocket = exposedSocket,
        )

        session.record { sequence, now ->
            WebSocketEvent.Connecting(
                sessionId = session.id,
                sequence = sequence,
                timestampNanos = now,
                url = request.url.toString(),
                headers = request.headers.toSafePairs(configuration.redactedHeaders),
            )
        }

        val realSocket = delegate.newWebSocket(request, monitoringListener)
        val monitoringSocket = MonitoringWebSocket(realSocket, session)
        exposedSocket.set(monitoringSocket)
        return monitoringSocket
    }

    data class Configuration(
        val maxBinaryPayloadBytes: Int = DEFAULT_MAX_BINARY_PAYLOAD_BYTES,
        val redactedHeaders: Set<String> = DEFAULT_REDACTED_HEADERS,
        val sessionIdProvider: () -> String = { UUID.randomUUID().toString() },
    ) {
        init {
            require(maxBinaryPayloadBytes >= 0) {
                "maxBinaryPayloadBytes must not be negative"
            }
        }

        private companion object {
            const val DEFAULT_MAX_BINARY_PAYLOAD_BYTES = 256 * 1024
            val DEFAULT_REDACTED_HEADERS = setOf("Authorization", "Cookie", "Set-Cookie")
        }
    }
}

private class MonitoringWebSocket(
    private val delegate: WebSocket,
    private val session: SessionRecorder,
) : WebSocket by delegate {

    override fun send(text: String): Boolean {
        val accepted = delegate.send(text)
        session.record { sequence, now ->
            WebSocketEvent.TextMessage(
                sessionId = session.id,
                sequence = sequence,
                timestampNanos = now,
                direction = Direction.OUTGOING,
                text = text,
                accepted = accepted,
            )
        }
        return accepted
    }

    override fun send(bytes: ByteString): Boolean {
        val accepted = delegate.send(bytes)
        session.recordBinary(
            direction = Direction.OUTGOING,
            bytes = bytes,
            accepted = accepted,
        )
        return accepted
    }

    override fun close(code: Int, reason: String?): Boolean {
        val accepted = delegate.close(code, reason)
        session.record { sequence, now ->
            WebSocketEvent.Closing(
                sessionId = session.id,
                sequence = sequence,
                timestampNanos = now,
                origin = CloseOrigin.LOCAL,
                code = code,
                reason = reason,
                accepted = accepted,
            )
        }
        return accepted
    }

    override fun cancel() {
        delegate.cancel()
        session.record { sequence, now ->
            WebSocketEvent.Cancelled(session.id, sequence, now)
        }
    }
}

private class MonitoringWebSocketListener(
    private val delegate: WebSocketListener,
    private val session: SessionRecorder,
    private val exposedSocket: AtomicReference<WebSocket>,
) : WebSocketListener() {

    override fun onOpen(webSocket: WebSocket, response: Response) {
        try {
            delegate.onOpen(exposedSocket.get() ?: webSocket, response)
        } finally {
            session.record { sequence, now ->
                WebSocketEvent.Opened(
                    sessionId = session.id,
                    sequence = sequence,
                    timestampNanos = now,
                    responseCode = response.code,
                    responseHeaders = response.headers.toSafePairs(emptySet()),
                )
            }
        }
    }

    override fun onMessage(webSocket: WebSocket, text: String) {
        try {
            delegate.onMessage(exposedSocket.get() ?: webSocket, text)
        } finally {
            session.record { sequence, now ->
                WebSocketEvent.TextMessage(
                    sessionId = session.id,
                    sequence = sequence,
                    timestampNanos = now,
                    direction = Direction.INCOMING,
                    text = text,
                )
            }
        }
    }

    override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
        try {
            delegate.onMessage(exposedSocket.get() ?: webSocket, bytes)
        } finally {
            session.recordBinary(Direction.INCOMING, bytes)
        }
    }

    override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
        try {
            delegate.onClosing(exposedSocket.get() ?: webSocket, code, reason)
        } finally {
            session.record { sequence, now ->
                WebSocketEvent.Closing(
                    sessionId = session.id,
                    sequence = sequence,
                    timestampNanos = now,
                    origin = CloseOrigin.REMOTE,
                    code = code,
                    reason = reason,
                )
            }
        }
    }

    override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
        try {
            delegate.onClosed(exposedSocket.get() ?: webSocket, code, reason)
        } finally {
            session.record { sequence, now ->
                WebSocketEvent.Closed(session.id, sequence, now, code, reason)
            }
        }
    }

    override fun onFailure(
        webSocket: WebSocket,
        t: Throwable,
        response: Response?,
    ) {
        try {
            delegate.onFailure(exposedSocket.get() ?: webSocket, t, response)
        } finally {
            session.record { sequence, now ->
                WebSocketEvent.Failure(
                    sessionId = session.id,
                    sequence = sequence,
                    timestampNanos = now,
                    throwable = t,
                    responseCode = response?.code,
                )
            }
        }
    }
}

private class SessionRecorder(
    val id: String,
    private val recorder: WebSocketRecorder,
    private val maxBinaryPayloadBytes: Int,
) {
    private val sequence = AtomicLong()

    inline fun record(event: (sequence: Long, timestampNanos: Long) -> WebSocketEvent) {
        val snapshot = event(sequence.getAndIncrement(), System.nanoTime())
        try {
            recorder.record(snapshot)
        } catch (_: Throwable) {
            // Recorder behavior is isolated from the transport and app listener.
        }
    }

    fun recordBinary(
        direction: Direction,
        bytes: ByteString,
        accepted: Boolean? = null,
    ) {
        record { sequence, now ->
            val capturedSize = minOf(bytes.size, maxBinaryPayloadBytes)
            WebSocketEvent.BinaryMessage(
                sessionId = id,
                sequence = sequence,
                timestampNanos = now,
                direction = direction,
                bytes = bytes.substring(0, capturedSize).toByteArray(),
                originalSize = bytes.size,
                truncated = capturedSize < bytes.size,
                accepted = accepted,
            )
        }
    }
}

private fun okhttp3.Headers.toSafePairs(
    redactedHeaders: Set<String>,
): List<Pair<String, String>> {
    if (size == 0) return emptyList()
    return List(size) { index ->
        val name = name(index)
        val value = if (redactedHeaders.any { it.equals(name, ignoreCase = true) }) {
            REDACTED_VALUE
        } else {
            value(index)
        }
        name to value
    }
}

private const val REDACTED_VALUE = "██"
