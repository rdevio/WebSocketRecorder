# WebSocketRecorder

A passive, listener-only WebSocket inspector for Kotlin and OkHttp.

WebSocketRecorder observes connection state and incoming messages by decorating the application's
existing `WebSocketListener`. It does not create, return or wrap a `WebSocket`. The application
remains the sole owner of connection creation, sending, closing, cancellation and reconnection.

> **Status:** early MVP. The event model, non-blocking recorder and OkHttp integration are usable.
> Android persistence and inspector UI are planned next.

## Design guarantee

Socket traffic always has priority over monitoring:

- Application callbacks run before monitoring callbacks.
- Recorder failures are suppressed.
- `AsyncWebSocketRecorder.record` uses a bounded, non-blocking queue.
- Events are dropped when the monitor is overloaded.
- The original `send`, `close` and `cancel` calls execute first.
- Original return values and application exceptions are preserved.

No observer can have literally zero CPU/allocation overhead. The contract is therefore behavioral:
monitoring must not control or block the socket, and overload must degrade recording rather than the
transport.

## Modules

- `recorder-core`: transport-independent events and bounded asynchronous recorder.
- `recorder-okhttp`: listener-only `WebSocketListener` decorator.
- `recorder-no-op`: release-safe recorder implementation.

## Usage

```kotlin
val asyncRecorder = AsyncWebSocketRecorder(capacity = 512) { event ->
    // Persist or forward on this background consumer.
}

val appListener = object : WebSocketListener() {
    // The application's existing listener.
}

val monitoredListener = appListener.withMonitoring(asyncRecorder)

// The application creates and owns its WebSocket exactly as before.
val webSocket = okHttpClient.newWebSocket(request, monitoredListener)
```

Every callback is forwarded to `appListener` with the exact original `WebSocket` instance.

### Listener-only limitation

`WebSocketListener` receives connection lifecycle events and incoming messages. OkHttp does not
report calls to `WebSocket.send(...)` through the listener, so a listener-only monitor cannot
observe outgoing messages. WebSocketRecorder deliberately accepts this limitation instead of
wrapping or owning the WebSocket.

### Release build

Use `NoOpWebSocketRecorder` when recording is disabled:

```kotlin
val listener = appListener.withMonitoring(NoOpWebSocketRecorder)
```

## Privacy

`Authorization`, `Cookie` and `Set-Cookie` request headers are redacted by default. Recorded message
payloads may still contain credentials or personal data. This project is intended for development
and controlled diagnostics; do not enable payload recording in production without an explicit data
retention and redaction policy.

## Roadmap

- Android Room persistence with retention and batch writes
- Jetpack Compose session/message inspector
- Payload redaction and recording levels
- Notification and in-app launcher
- JSON export
- Benchmarks for latency, throughput and allocation overhead

## License

Apache License 2.0. See `LICENSE`.
