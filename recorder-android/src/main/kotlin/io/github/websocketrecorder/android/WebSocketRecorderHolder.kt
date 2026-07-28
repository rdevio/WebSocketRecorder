package io.github.websocketrecorder.android

import android.content.Context
import io.github.websocketrecorder.core.WebSocketRecorder

/**
 * Process-wide holder for applications that want a single Android recorder instance.
 */
object WebSocketRecorderHolder {
    @Volatile
    private var instance: AndroidWebSocketRecorder? = null

    @JvmStatic
    fun initialize(context: Context): AndroidWebSocketRecorder {
        instance?.let { return it }

        return synchronized(this) {
            instance ?: AndroidWebSocketRecorder.Builder(context.applicationContext)
                .build()
                .also { instance = it }
        }
    }

    @JvmStatic
    fun get(): WebSocketRecorder? = instance

    @JvmStatic
    fun requireRecorder(): WebSocketRecorder = checkNotNull(instance) {
        "WebSocketRecorderHolder is not initialized. Call initialize(context) first."
    }

    @JvmStatic
    fun install(recorder: AndroidWebSocketRecorder) {
        synchronized(this) {
            instance?.close()
            instance = recorder
        }
    }

    @JvmStatic
    fun close() {
        synchronized(this) {
            instance?.close()
            instance = null
        }
    }
}
