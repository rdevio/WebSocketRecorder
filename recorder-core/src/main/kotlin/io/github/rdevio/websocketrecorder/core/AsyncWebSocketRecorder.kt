package io.github.rdevio.websocketrecorder.core

import java.io.Closeable
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import kotlin.concurrent.thread

/**
 * Moves event consumption off the WebSocket callback thread.
 *
 * When overloaded, events are dropped instead of blocking the socket. This behavior is deliberate.
 */
class AsyncWebSocketRecorder(
    capacity: Int = DEFAULT_CAPACITY,
    private val consumer: (WebSocketEvent) -> Unit,
) : WebSocketRecorder, Closeable {
    private val queue = ArrayBlockingQueue<WebSocketEvent>(capacity)
    private val running = AtomicBoolean(true)
    private val dropped = AtomicLong()
    private val worker = thread(
        start = true,
        isDaemon = true,
        name = "websocket-recorder",
    ) {
        consumeEvents()
    }

    init {
        require(capacity > 0) { "capacity must be greater than zero" }
    }

    val droppedEventCount: Long
        get() = dropped.get()

    override fun record(event: WebSocketEvent) {
        if (!running.get() || !queue.offer(event)) {
            dropped.incrementAndGet()
        }
    }

    override fun close() {
        if (running.compareAndSet(true, false)) {
            worker.interrupt()
        }
    }

    private fun consumeEvents() {
        while (running.get() || queue.isNotEmpty()) {
            val event = try {
                queue.poll(POLL_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS)
            } catch (_: InterruptedException) {
                continue
            }

            if (event != null) {
                try {
                    consumer(event)
                } catch (_: Throwable) {
                    // A broken storage/UI consumer cannot terminate monitoring or affect the socket.
                }
            }
        }
    }

    private companion object {
        const val DEFAULT_CAPACITY = 512
        const val POLL_TIMEOUT_MILLIS = 100L
    }
}
