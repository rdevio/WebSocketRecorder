package io.github.rdevio.websocketrecorder.noop

import io.github.rdevio.websocketrecorder.core.WebSocketRecorder

/**
 * Release-safe recorder that performs no allocation or persistence for events.
 */
object NoOpWebSocketRecorder : WebSocketRecorder {
    override fun record(event: io.github.rdevio.websocketrecorder.core.WebSocketEvent) = Unit
}
