package io.github.websocketrecorder.noop

import io.github.websocketrecorder.core.WebSocketRecorder

/**
 * Release-safe recorder that performs no allocation or persistence for events.
 */
object NoOpWebSocketRecorder : WebSocketRecorder {
    override fun record(event: io.github.websocketrecorder.core.WebSocketEvent) = Unit
}
