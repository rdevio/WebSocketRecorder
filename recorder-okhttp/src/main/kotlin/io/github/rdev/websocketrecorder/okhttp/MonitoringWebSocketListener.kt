package io.github.rdev.websocketrecorder.okhttp

import io.github.rdev.websocketrecorder.core.CloseOrigin
import io.github.rdev.websocketrecorder.core.Direction
import io.github.rdev.websocketrecorder.core.WebSocketEvent
import io.github.rdev.websocketrecorder.core.WebSocketRecorder
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import java.util.UUID
import java.util.concurrent.atomic.AtomicLong

/**
 * Decorates an application's [WebSocketListener] without creating or wrapping a [WebSocket].
 *
 * The application remains the sole owner of WebSocket creation, sending, closing, cancellation,
 * connection settings and reconnection. Every callback receives the exact [WebSocket] instance
 * supplied by the underlying WebSocket implementation.
 *
 * Because OkHttp reports only incoming messages through [WebSocketListener], this decorator cannot
 * observe outgoing messages. Recording outgoing traffic requires an explicit signal from the
 * application and is intentionally outside this listener-only API.
 */
class MonitoringWebSocketListener(
    private val delegate: WebSocketListener,
    private val recorder: WebSocketRecorder,
    private val configuration: Configuration = Configuration(),
) : WebSocketListener() {
    private val sequence = AtomicLong()
    private val sessionId = configuration.sessionIdProvider()

    override fun onOpen(webSocket: WebSocket, response: Response) {
        try {
            delegate.onOpen(webSocket, response)
        } finally {
            record { sequence, now ->
                WebSocketEvent.Opened(
                    sessionId = sessionId,
                    sequence = sequence,
                    timestampNanos = now,
                    responseCode = response.code,
                    responseHeaders = response.headers.toSafePairs(configuration.redactedHeaders),
                )
            }
        }
    }

    override fun onMessage(webSocket: WebSocket, text: String) {
        try {
            delegate.onMessage(webSocket, text)
        } finally {
            record { sequence, now ->
                WebSocketEvent.TextMessage(
                    sessionId = sessionId,
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
            delegate.onMessage(webSocket, bytes)
        } finally {
            recordBinary(bytes)
        }
    }

    override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
        try {
            delegate.onClosing(webSocket, code, reason)
        } finally {
            record { sequence, now ->
                WebSocketEvent.Closing(
                    sessionId = sessionId,
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
            delegate.onClosed(webSocket, code, reason)
        } finally {
            record { sequence, now ->
                WebSocketEvent.Closed(sessionId, sequence, now, code, reason)
            }
        }
    }

    override fun onFailure(
        webSocket: WebSocket,
        t: Throwable,
        response: Response?,
    ) {
        try {
            delegate.onFailure(webSocket, t, response)
        } finally {
            record { sequence, now ->
                WebSocketEvent.Failure(
                    sessionId = sessionId,
                    sequence = sequence,
                    timestampNanos = now,
                    throwable = t,
                    responseCode = response?.code,
                )
            }
        }
    }

    private fun recordBinary(bytes: ByteString) {
        record { sequence, now ->
            val capturedSize = minOf(bytes.size, configuration.maxBinaryPayloadBytes)
            WebSocketEvent.BinaryMessage(
                sessionId = sessionId,
                sequence = sequence,
                timestampNanos = now,
                direction = Direction.INCOMING,
                bytes = bytes.substring(0, capturedSize).toByteArray(),
                originalSize = bytes.size,
                truncated = capturedSize < bytes.size,
            )
        }
    }

    private inline fun record(
        event: (sequence: Long, timestampNanos: Long) -> WebSocketEvent,
    ) {
        val snapshot = event(sequence.getAndIncrement(), System.nanoTime())
        try {
            recorder.record(snapshot)
        } catch (_: Throwable) {
            // Monitoring must never affect the application's listener or WebSocket.
        }
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

fun WebSocketListener.withMonitoring(
    recorder: WebSocketRecorder,
    configuration: MonitoringWebSocketListener.Configuration =
        MonitoringWebSocketListener.Configuration(),
): WebSocketListener = MonitoringWebSocketListener(
    delegate = this,
    recorder = recorder,
    configuration = configuration,
)

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
