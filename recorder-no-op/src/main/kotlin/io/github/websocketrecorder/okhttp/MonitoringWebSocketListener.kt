package io.github.websocketrecorder.okhttp

import io.github.websocketrecorder.core.WebSocketRecorder
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import java.util.UUID

/**
 * Release-safe API-compatible replacement for the recording listener.
 *
 * It returns the application's listener unchanged and performs no recording or allocation beyond
 * the call itself.
 */
@Suppress("UNUSED_PARAMETER")
fun WebSocketListener.withMonitoring(
    recorder: WebSocketRecorder,
): WebSocketListener = this

@Suppress("UNUSED_PARAMETER")
fun WebSocketListener.withMonitoring(
    recorder: WebSocketRecorder,
    configuration: MonitoringWebSocketListener.Configuration,
): WebSocketListener = this

/**
 * Holds the same public configuration shape as the recording artifact.
 *
 * Prefer [WebSocketListener.withMonitoring], which returns the original listener unchanged in this
 * no-op artifact.
 */
class MonitoringWebSocketListener(
    private val delegate: WebSocketListener,
    recorder: WebSocketRecorder,
    configuration: Configuration = Configuration(),
) : WebSocketListener() {
    override fun onOpen(webSocket: WebSocket, response: Response) =
        delegate.onOpen(webSocket, response)

    override fun onMessage(webSocket: WebSocket, text: String) =
        delegate.onMessage(webSocket, text)

    override fun onMessage(webSocket: WebSocket, bytes: ByteString) =
        delegate.onMessage(webSocket, bytes)

    override fun onClosing(webSocket: WebSocket, code: Int, reason: String) =
        delegate.onClosing(webSocket, code, reason)

    override fun onClosed(webSocket: WebSocket, code: Int, reason: String) =
        delegate.onClosed(webSocket, code, reason)

    override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) =
        delegate.onFailure(webSocket, t, response)

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
