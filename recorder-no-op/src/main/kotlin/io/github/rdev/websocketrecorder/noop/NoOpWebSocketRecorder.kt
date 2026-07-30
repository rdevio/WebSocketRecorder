package io.github.rdev.websocketrecorder.noop

import io.github.rdev.websocketrecorder.core.WebSocketRecorder

/**
 * Release-safe recorder that performs no allocation or persistence for events.
 */
object NoOpWebSocketRecorder : WebSocketRecorder {
    override fun record(event: io.github.rdev.websocketrecorder.core.WebSocketEvent) = Unit
}
