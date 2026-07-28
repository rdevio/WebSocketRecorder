package io.github.websocketrecorder.android

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.util.Base64
import io.github.websocketrecorder.core.WebSocketEvent

internal class RecorderDatabase(
    context: Context,
    private val maxStoredEvents: Int,
) : SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE events (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                session_id TEXT NOT NULL,
                sequence_number INTEGER NOT NULL,
                timestamp_nanos INTEGER NOT NULL,
                event_type TEXT NOT NULL,
                title TEXT NOT NULL,
                payload TEXT,
                is_json_candidate INTEGER NOT NULL DEFAULT 0
            )
            """.trimIndent(),
        )
        db.execSQL("CREATE INDEX index_events_session ON events(session_id, sequence_number)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit

    fun insert(event: WebSocketEvent): Long {
        val display = event.toDisplayValue()
        val values = ContentValues().apply {
            put("session_id", event.sessionId)
            put("sequence_number", event.sequence)
            put("timestamp_nanos", event.timestampNanos)
            put("event_type", event.javaClass.simpleName)
            put("title", display.title)
            put("payload", display.payload)
            put("is_json_candidate", if (display.isJsonCandidate) 1 else 0)
        }
        val database = writableDatabase
        val id = database.insertOrThrow("events", null, values)
        if (id % PRUNE_INTERVAL == 0L) {
            database.execSQL(
                """
                DELETE FROM events
                WHERE id NOT IN (
                    SELECT id FROM events ORDER BY id DESC LIMIT ?
                )
                """.trimIndent(),
                arrayOf(maxStoredEvents),
            )
        }
        return id
    }

    fun count(): Long = readableDatabase.rawQuery(
        "SELECT COUNT(*) FROM events",
        null,
    ).use { cursor ->
        if (cursor.moveToFirst()) cursor.getLong(0) else 0L
    }

    fun recent(limit: Int = 1_000): List<StoredEvent> = readableDatabase.rawQuery(
        """
        SELECT id, title, payload, is_json_candidate, session_id
        FROM events
        ORDER BY id DESC
        LIMIT ?
        """.trimIndent(),
        arrayOf(limit.toString()),
    ).use { cursor ->
        buildList {
            while (cursor.moveToNext()) {
                add(
                    StoredEvent(
                        id = cursor.getLong(0),
                        title = cursor.getString(1),
                        payload = cursor.getString(2),
                        isJsonCandidate = cursor.getInt(3) == 1,
                        sessionId = cursor.getString(4),
                    ),
                )
            }
        }
    }

    fun clear() {
        writableDatabase.delete("events", null, null)
    }

    private fun WebSocketEvent.toDisplayValue(): DisplayValue = when (this) {
        is WebSocketEvent.TextMessage -> DisplayValue(
            title = "IN  ${text.singleLinePreview()}",
            payload = text,
            isJsonCandidate = true,
        )

        is WebSocketEvent.BinaryMessage -> DisplayValue(
            title = "IN  Binary · $originalSize bytes${if (truncated) " · truncated" else ""}",
            payload = Base64.encodeToString(bytes, Base64.NO_WRAP),
        )

        is WebSocketEvent.Opened -> DisplayValue(
            title = "OPEN  HTTP $responseCode",
            payload = responseHeaders.joinToString("\n") { (name, value) -> "$name: $value" },
        )

        is WebSocketEvent.Closing -> DisplayValue(
            title = "CLOSING  $code",
            payload = reason,
        )

        is WebSocketEvent.Closed -> DisplayValue(
            title = "CLOSED  $code",
            payload = reason,
        )

        is WebSocketEvent.Failure -> DisplayValue(
            title = "FAILED  ${throwable.message.orEmpty().singleLinePreview()}",
            payload = throwable.stackTraceToString(),
        )

        is WebSocketEvent.Connecting -> DisplayValue(
            title = "CONNECTING  ${url.singleLinePreview()}",
            payload = headers.joinToString("\n") { (name, value) -> "$name: $value" },
        )

        is WebSocketEvent.Cancelled -> DisplayValue(title = "CANCELLED")
    }

    private data class DisplayValue(
        val title: String,
        val payload: String? = null,
        val isJsonCandidate: Boolean = false,
    )

    private companion object {
        const val DATABASE_NAME = "websocket-recorder.db"
        const val DATABASE_VERSION = 1
        const val PRUNE_INTERVAL = 100L
    }
}

internal data class StoredEvent(
    val id: Long,
    val title: String,
    val payload: String?,
    val isJsonCandidate: Boolean,
    val sessionId: String,
)
