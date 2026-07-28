package io.github.websocketrecorder.android

import android.content.Context
import io.github.websocketrecorder.core.AsyncWebSocketRecorder
import io.github.websocketrecorder.core.WebSocketEvent
import io.github.websocketrecorder.core.WebSocketRecorder
import java.io.Closeable
import java.util.concurrent.atomic.AtomicLong

/**
 * Android collector that moves persistence and notifications off the WebSocket callback thread.
 *
 * A full queue drops monitoring events instead of delaying the socket.
 */
class AndroidWebSocketRecorder private constructor(
    context: Context,
    private val configuration: Configuration,
) : WebSocketRecorder, Closeable {
    private val database = RecorderDatabase(
        context = context.applicationContext,
        maxStoredEvents = configuration.maxStoredEvents,
        messageMasker = configuration.messageMasker,
    )
    private val notifications = RecorderNotifications(
        context = context.applicationContext,
        enabled = configuration.showNotification,
    )
    private val storedEventCount = AtomicLong(database.count())
    private val outgoingSequence = AtomicLong()
    private val asyncRecorder = AsyncWebSocketRecorder(
        capacity = configuration.queueCapacity,
    ) { event ->
        database.insert(event)?.let { id ->
            val count = storedEventCount.incrementAndGet()
            notifications.onEventStored(id, count, event.summary())
        }
    }

    override fun record(event: WebSocketEvent) {
        asyncRecorder.record(event)
    }

    fun recordOutgoing(text: String) {
        asyncRecorder.record(
            WebSocketEvent.TextMessage(
                sessionId = OUTGOING_SESSION,
                sequence = outgoingSequence.getAndIncrement(),
                timestampNanos = System.nanoTime(),
                direction = io.github.websocketrecorder.core.Direction.OUTGOING,
                text = text,
                accepted = true,
            ),
        )
    }

    override fun close() {
        asyncRecorder.close()
    }

    val droppedEventCount: Long
        get() = asyncRecorder.droppedEventCount

    data class Configuration(
        val queueCapacity: Int = 512,
        val maxStoredEvents: Int = 10_000,
        val showNotification: Boolean = true,
        val messageMasker: WebSocketMessageMasker = WebSocketMessageMasker.KEEP_ALL,
    ) {
        init {
            require(queueCapacity > 0) { "queueCapacity must be greater than zero" }
            require(maxStoredEvents > 0) { "maxStoredEvents must be greater than zero" }
        }
    }

    class Builder(private val context: Context) {
        private var queueCapacity: Int = 512
        private var maxStoredEvents: Int = 10_000
        private var showNotification: Boolean = true
        private var messageMasker: WebSocketMessageMasker = WebSocketMessageMasker.KEEP_ALL

        fun queueCapacity(value: Int) = apply { queueCapacity = value }

        fun maxStoredEvents(value: Int) = apply { maxStoredEvents = value }

        fun showNotification(value: Boolean) = apply { showNotification = value }

        fun messageMasker(value: WebSocketMessageMasker) = apply { messageMasker = value }

        fun build(): AndroidWebSocketRecorder = AndroidWebSocketRecorder(
            context = context,
            configuration = Configuration(
                queueCapacity = queueCapacity,
                maxStoredEvents = maxStoredEvents,
                showNotification = showNotification,
                messageMasker = messageMasker,
            ),
        )
    }

    private companion object {
        const val OUTGOING_SESSION = "outgoing"
    }
}

private fun WebSocketEvent.summary(): String = when (this) {
    is WebSocketEvent.TextMessage -> "Incoming: ${text.singleLinePreview()}"
    is WebSocketEvent.BinaryMessage -> "Incoming binary · $originalSize bytes"
    is WebSocketEvent.Opened -> "WebSocket opened · HTTP $responseCode"
    is WebSocketEvent.Closing -> "WebSocket closing · $code"
    is WebSocketEvent.Closed -> "WebSocket closed · $code"
    is WebSocketEvent.Failure -> "WebSocket failed · ${throwable.message.orEmpty()}"
    is WebSocketEvent.Connecting -> "WebSocket connecting"
    is WebSocketEvent.Cancelled -> "WebSocket cancelled"
}

internal fun String.singleLinePreview(maxLength: Int = 80): String {
    val singleLine = replace('\n', ' ').replace('\r', ' ').trim()
    return if (singleLine.length <= maxLength) singleLine else singleLine.take(maxLength) + "…"
}
