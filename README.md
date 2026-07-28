# WebSocketRecorder

A passive, best-effort WebSocket inspector for Kotlin and OkHttp.

WebSocketRecorder observes connection state and messages without owning the connection. It never
reconnects, retries, changes payloads, changes return values, or waits for storage/UI work on an
OkHttp callback thread.

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
- `recorder-okhttp`: transparent `WebSocket.Factory` decorator.
- `recorder-no-op`: release-safe recorder implementation.

## Usage

```kotlin
val asyncRecorder = AsyncWebSocketRecorder(capacity = 512) { event ->
    // Persist or forward on this background consumer.
}

val monitoredFactory = MonitoringWebSocketFactory(
    delegate = okHttpClient,
    recorder = asyncRecorder,
)

val webSocket = monitoredFactory.newWebSocket(request, appListener)
```

The returned `WebSocket` delegates to the real OkHttp socket. Incoming lifecycle callbacks are
forwarded to `appListener`. Outgoing messages are observed through the returned decorator.

### Release build

Use `NoOpWebSocketRecorder` when recording is disabled:

```kotlin
val monitoredFactory = MonitoringWebSocketFactory(
    delegate = okHttpClient,
    recorder = NoOpWebSocketRecorder,
)
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
