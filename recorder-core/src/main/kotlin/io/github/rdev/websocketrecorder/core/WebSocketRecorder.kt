package io.github.rdev.websocketrecorder.core

/**
 * A passive, best-effort observer.
 *
 * Implementations must return immediately, must not throw, and must prefer dropping monitoring
 * events over slowing down the WebSocket.
 */
fun interface WebSocketRecorder {
    fun record(event: WebSocketEvent)

    companion object {
        val NONE: WebSocketRecorder = WebSocketRecorder { }
    }
}

internal fun WebSocketRecorder.recordSafely(event: WebSocketEvent) {
    try {
        record(event)
    } catch (_: Throwable) {
        // Monitoring is never allowed to affect the WebSocket.
    }
}
