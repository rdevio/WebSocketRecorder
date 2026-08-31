# Changelog

All notable changes to WebSocketRecorder are documented here.

## [0.1.9] - 2026-08-31

### Fixed

- Restore the vertical message list and add long-press payload previews.

## [0.1.8] - 2026-08-31

### Fixed

- Preserve WebSocket messages with heterogeneous or non-object payloads.
- Display recorded messages in a horizontally scrollable inspector list.

## [0.1.7] - 2026-08-01

### Added

- Notification and an Android inspector activity for recorded WebSocket traffic.
- Real-time message updates without forcing the list to scroll when the user is reading older items.
- Outgoing message monitoring through a lightweight decorator around the existing OkHttp WebSocket.
- Request and response matching by `uniqueId`.
- Request time, response time, and total response duration.
- Separate colors for incoming and outgoing messages.
- Message titles based on `content.type`, with fallback to the top-level `type`.
- Type masking and the ability to ignore selected message types.
- Persistent message filters in the inspector.
- Socket URL display and dark theme support.
- Pretty JSON display with expandable and collapsible objects and arrays.
- Copy and text-file sharing for individual payloads and combined request/response pairs.

### Behavior

- The recorder does not create or own a WebSocket connection.
- Monitoring failures never change the original socket's send, close, or cancel result.
- Recording uses a bounded background queue and drops monitoring events under overload.

[0.1.7]: https://github.com/rdevio/WebSocketRecorder/releases/tag/v0.1.7
