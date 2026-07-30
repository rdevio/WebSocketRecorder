package io.github.rdev.websocketrecorder.core

/**
 * Immutable snapshots emitted by a monitored WebSocket.
 *
 * Payloads are intentionally left in their original immutable representation where possible.
 * Expensive formatting, decoding and persistence belong in an asynchronous consumer.
 */
sealed interface WebSocketEvent {
    val sessionId: String
    val sequence: Long
    val timestampNanos: Long

    data class Connecting(
        override val sessionId: String,
        override val sequence: Long,
        override val timestampNanos: Long,
        val url: String,
        val headers: List<Pair<String, String>>,
    ) : WebSocketEvent

    data class Opened(
        override val sessionId: String,
        override val sequence: Long,
        override val timestampNanos: Long,
        val responseCode: Int,
        val responseHeaders: List<Pair<String, String>>,
    ) : WebSocketEvent

    data class TextMessage(
        override val sessionId: String,
        override val sequence: Long,
        override val timestampNanos: Long,
        val direction: Direction,
        val text: String,
        val accepted: Boolean? = null,
    ) : WebSocketEvent

    data class BinaryMessage(
        override val sessionId: String,
        override val sequence: Long,
        override val timestampNanos: Long,
        val direction: Direction,
        val bytes: ByteArray,
        val originalSize: Int,
        val truncated: Boolean,
        val accepted: Boolean? = null,
    ) : WebSocketEvent

    data class Closing(
        override val sessionId: String,
        override val sequence: Long,
        override val timestampNanos: Long,
        val origin: CloseOrigin,
        val code: Int,
        val reason: String?,
        val accepted: Boolean? = null,
    ) : WebSocketEvent

    data class Closed(
        override val sessionId: String,
        override val sequence: Long,
        override val timestampNanos: Long,
        val code: Int,
        val reason: String,
    ) : WebSocketEvent

    data class Failure(
        override val sessionId: String,
        override val sequence: Long,
        override val timestampNanos: Long,
        val throwable: Throwable,
        val responseCode: Int?,
    ) : WebSocketEvent

    data class Cancelled(
        override val sessionId: String,
        override val sequence: Long,
        override val timestampNanos: Long,
    ) : WebSocketEvent
}

enum class Direction {
    INCOMING,
    OUTGOING,
}

enum class CloseOrigin {
    LOCAL,
    REMOTE,
}
