package io.github.rdev.websocketrecorder.okhttp

import io.github.rdev.websocketrecorder.core.Direction
import io.github.rdev.websocketrecorder.core.WebSocketEvent
import io.github.rdev.websocketrecorder.core.WebSocketRecorder
import okhttp3.Request
import okhttp3.WebSocket
import okio.ByteString
import java.util.UUID
import java.util.concurrent.atomic.AtomicLong

/**
 * Decorates an already-created [WebSocket] and observes successful outgoing messages.
 *
 * This class never creates or owns a connection. Every operation is delegated to the original
 * socket first. Recorder failures are ignored, so monitoring cannot change the result of [send].
 */
class MonitoringWebSocket(
    private val delegate: WebSocket,
    private val recorder: WebSocketRecorder,
    private val configuration: Configuration = Configuration(),
) : WebSocket {
    private val sequence = AtomicLong()
    private val sessionId = configuration.sessionIdProvider()

    override fun request(): Request = delegate.request()

    override fun queueSize(): Long = delegate.queueSize()

    override fun send(text: String): Boolean {
        val accepted = delegate.send(text)
        if (accepted) {
            recordSafely(
                WebSocketEvent.TextMessage(
                    sessionId = sessionId,
                    sequence = sequence.getAndIncrement(),
                    timestampNanos = System.nanoTime(),
                    direction = Direction.OUTGOING,
                    text = text,
                    accepted = true,
                ),
            )
        }
        return accepted
    }

    override fun send(bytes: ByteString): Boolean {
        val accepted = delegate.send(bytes)
        if (accepted) {
            val capturedSize = minOf(bytes.size, configuration.maxBinaryPayloadBytes)
            recordSafely(
                WebSocketEvent.BinaryMessage(
                    sessionId = sessionId,
                    sequence = sequence.getAndIncrement(),
                    timestampNanos = System.nanoTime(),
                    direction = Direction.OUTGOING,
                    bytes = bytes.substring(0, capturedSize).toByteArray(),
                    originalSize = bytes.size,
                    truncated = capturedSize < bytes.size,
                    accepted = true,
                ),
            )
        }
        return accepted
    }

    override fun close(code: Int, reason: String?): Boolean = delegate.close(code, reason)

    override fun cancel() = delegate.cancel()

    private fun recordSafely(event: WebSocketEvent) {
        try {
            recorder.record(event)
        } catch (_: Throwable) {
            // Monitoring must never affect the application's WebSocket.
        }
    }

    data class Configuration(
        val maxBinaryPayloadBytes: Int = DEFAULT_MAX_BINARY_PAYLOAD_BYTES,
        val sessionIdProvider: () -> String = { UUID.randomUUID().toString() },
    ) {
        init {
            require(maxBinaryPayloadBytes >= 0) {
                "maxBinaryPayloadBytes must not be negative"
            }
        }

        private companion object {
            const val DEFAULT_MAX_BINARY_PAYLOAD_BYTES = 256 * 1024
        }
    }
}

fun WebSocket.withMonitoring(
    recorder: WebSocketRecorder,
    configuration: MonitoringWebSocket.Configuration = MonitoringWebSocket.Configuration(),
): WebSocket = MonitoringWebSocket(
    delegate = this,
    recorder = recorder,
    configuration = configuration,
)
