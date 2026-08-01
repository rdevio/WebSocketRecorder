# WebSocketRecorder

WebSocketRecorder is an Android debug tool for monitoring an existing OkHttp WebSocket.

It shows:

- connection state
- sent and received messages
- request/response pairs matched by `uniqueId`
- message duration and timestamps
- pretty, collapsible JSON
- notification with a shortcut to the inspector

The library does not create a WebSocket connection. Your application still owns the original
WebSocket and its listener.

## Installation

Add Maven Central if it is not already available:

```kotlin
repositories {
    google()
    mavenCentral()
}
```

Add the debug libraries and the release no-op implementation:

```kotlin
dependencies {
    debugImplementation("io.github.rdevio.websocketrecorder:recorder-android:0.1.7")
    debugImplementation("io.github.rdevio.websocketrecorder:recorder-okhttp:0.1.7")

    releaseImplementation("io.github.rdevio.websocketrecorder:recorder-no-op:0.1.7")
}
```

## Quick start

Initialize the recorder once, usually in your debug `Application`:

```kotlin
WebSocketRecorderHolder.initialize(applicationContext)
```

Decorate your existing listener:

```kotlin
val monitoredListener = appListener.withMonitoring(
    WebSocketRecorderHolder.requireRecorder(),
)

val socket = okHttpClient.newWebSocket(request, monitoredListener)
```

This records connection events and incoming messages. The same listener and the same WebSocket
instance are still used by your application.

## Record outgoing messages

OkHttp does not send `WebSocket.send(...)` calls to `WebSocketListener`. To monitor outgoing
messages, decorate the WebSocket returned by OkHttp:

```kotlin
val recorder = WebSocketRecorderHolder.requireRecorder()

val socket = okHttpClient
    .newWebSocket(request, appListener.withMonitoring(recorder))
    .withMonitoring(recorder)

socket.send(message)
```

`withMonitoring(recorder)` does not open a second connection. It delegates to the original socket
and only records successful outgoing sends.

## Message names and ignored types

Map numeric message types to readable names, or return `null` to ignore a type completely:

```kotlin
val recorder = AndroidWebSocketRecorder.Builder(applicationContext)
    .messageMasker { type ->
        when (type) {
            "0" -> "ASYNC_PING"
            "6" -> "CHAT_PING"
            "23" -> "GET_USER_PROFILE"
            "24" -> "UPDATE_USER_PROFILE"
            "1", "2", "99" -> null
            else -> type
        }
    }
    .build()

WebSocketRecorderHolder.install(recorder)
```

Java:

```java
AndroidWebSocketRecorder recorder = new AndroidWebSocketRecorder.Builder(getApplicationContext())
    .messageMasker(type -> {
        switch (type) {
            case "0": return "ASYNC_PING";
            case "6": return "CHAT_PING";
            case "23": return "GET_USER_PROFILE";
            case "24": return "UPDATE_USER_PROFILE";
            case "1":
            case "2":
            case "99": return null;
            default: return type;
        }
    })
    .build();

WebSocketRecorderHolder.install(recorder);
```

Returning `null` means the message is not stored and does not appear in the notification or list.

## Open the inspector

Tapping the notification opens the message list. You can also open it directly:

```kotlin
RecorderActivity.launch(context)
```

The inspector supports filtering, dark theme, copy, and sharing request/response data as a text
file. JSON objects and arrays can be expanded and collapsed.

On Android 13 and newer, request `POST_NOTIFICATIONS` in the host application if you want the
notification to be visible. Recording and the inspector still work without that permission.

## How request and response messages are matched

When an outgoing request and incoming response contain the same `uniqueId`, the inspector merges
them into one list item. It displays request time, response time, and total duration.

For the title, the recorder first reads `content.type`, then falls back to the top-level `type`.
Stringified JSON inside `content` is decoded before display.

## Safety

Monitoring is best-effort and must not control the socket:

- application callbacks run before recorder callbacks
- recorder errors do not crash socket callbacks
- recording uses a bounded background queue
- events are dropped when the recorder is overloaded
- original `send`, `close`, and `cancel` behavior is preserved

Use this library for development and controlled diagnostics. Message payloads may contain personal
or sensitive data.

## Modules

- `recorder-android`: notification, storage, and inspector UI
- `recorder-okhttp`: OkHttp listener and WebSocket decorators
- `recorder-core`: transport-independent event model
- `recorder-no-op`: release-safe no-op recorder

## Maven Local

To test an unpublished build locally:

```shell
./gradlew publishToMavenLocal
```

Then add `mavenLocal()` before `mavenCentral()` in the consuming project.

## License

Apache License 2.0. See [LICENSE](LICENSE).
