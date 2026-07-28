package io.github.websocketrecorder.android

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.util.Base64
import io.github.websocketrecorder.core.Direction
import io.github.websocketrecorder.core.WebSocketEvent
import java.util.concurrent.CopyOnWriteArraySet

internal class RecorderDatabase(
    context: Context,
    private val maxStoredEvents: Int,
) : SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE exchanges (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                correlation_key TEXT NOT NULL UNIQUE,
                unique_id TEXT,
                type_label TEXT NOT NULL,
                request_payload TEXT,
                response_payload TEXT,
                request_time_ms INTEGER,
                response_time_ms INTEGER,
                updated_time_ms INTEGER NOT NULL
            )
            """.trimIndent(),
        )
        db.execSQL("CREATE INDEX index_exchanges_updated ON exchanges(updated_time_ms)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL("DROP TABLE IF EXISTS events")
        db.execSQL("DROP TABLE IF EXISTS exchanges")
        onCreate(db)
    }

    fun insert(event: WebSocketEvent): Long {
        val now = System.currentTimeMillis()
        val id = when (event) {
            is WebSocketEvent.TextMessage -> upsertText(event, now)
            else -> insertStandalone(event, now)
        }
        prune()
        notifyChanged()
        return id
    }

    private fun upsertText(event: WebSocketEvent.TextMessage, now: Long): Long {
        val parsed = RecordedPayloadParser.parse(event.text)
        val key = parsed.uniqueId?.let { "unique:$it" }
            ?: "event:${event.sessionId}:${event.sequence}:${event.direction}"
        val existingId = findId(key)
        val values = ContentValues().apply {
            put("correlation_key", key)
            put("unique_id", parsed.uniqueId)
            put("type_label", parsed.type)
            put("updated_time_ms", now)
            if (event.direction == Direction.OUTGOING) {
                put("request_payload", parsed.normalized)
                put("request_time_ms", now)
            } else {
                put("response_payload", parsed.normalized)
                put("response_time_ms", now)
            }
        }
        return if (existingId == null) {
            writableDatabase.insertOrThrow("exchanges", null, values)
        } else {
            writableDatabase.update(
                "exchanges",
                values,
                "id = ?",
                arrayOf(existingId.toString()),
            )
            existingId
        }
    }

    private fun insertStandalone(event: WebSocketEvent, now: Long): Long {
        val display = event.toDisplay()
        return writableDatabase.insertOrThrow(
            "exchanges",
            null,
            ContentValues().apply {
                put("correlation_key", "event:${event.sessionId}:${event.sequence}:${event.javaClass.simpleName}")
                put("type_label", display.first)
                put("response_payload", display.second)
                put("response_time_ms", now)
                put("updated_time_ms", now)
            },
        )
    }

    private fun findId(key: String): Long? = readableDatabase.query(
        "exchanges",
        arrayOf("id"),
        "correlation_key = ?",
        arrayOf(key),
        null,
        null,
        null,
        "1",
    ).use { if (it.moveToFirst()) it.getLong(0) else null }

    fun count(): Long = readableDatabase.rawQuery(
        "SELECT COUNT(*) FROM exchanges",
        null,
    ).use { if (it.moveToFirst()) it.getLong(0) else 0L }

    fun recent(limit: Int = 1_000): List<StoredExchange> = readableDatabase.rawQuery(
        """
        SELECT id, unique_id, type_label, request_payload, response_payload,
               request_time_ms, response_time_ms, updated_time_ms
        FROM exchanges ORDER BY updated_time_ms DESC LIMIT ?
        """.trimIndent(),
        arrayOf(limit.toString()),
    ).use { cursor ->
        buildList {
            while (cursor.moveToNext()) {
                add(
                    StoredExchange(
                        id = cursor.getLong(0),
                        uniqueId = cursor.getString(1),
                        type = cursor.getString(2),
                        request = cursor.getString(3),
                        response = cursor.getString(4),
                        requestTimeMs = cursor.longOrNull(5),
                        responseTimeMs = cursor.longOrNull(6),
                        updatedTimeMs = cursor.getLong(7),
                    ),
                )
            }
        }
    }

    fun clear() {
        writableDatabase.delete("exchanges", null, null)
        notifyChanged()
    }

    fun addChangeListener(listener: () -> Unit) {
        listeners += listener
    }

    fun removeChangeListener(listener: () -> Unit) {
        listeners -= listener
    }

    private fun prune() {
        writableDatabase.execSQL(
            """
            DELETE FROM exchanges WHERE id NOT IN (
                SELECT id FROM exchanges ORDER BY updated_time_ms DESC LIMIT ?
            )
            """.trimIndent(),
            arrayOf(maxStoredEvents),
        )
    }

    private fun WebSocketEvent.toDisplay(): Pair<String, String?> = when (this) {
        is WebSocketEvent.BinaryMessage ->
            "Binary · $originalSize bytes" to Base64.encodeToString(bytes, Base64.NO_WRAP)
        is WebSocketEvent.Opened ->
            "OPEN · HTTP $responseCode" to responseHeaders.joinToString("\n") { "${it.first}: ${it.second}" }
        is WebSocketEvent.Closing -> "CLOSING · $code" to reason
        is WebSocketEvent.Closed -> "CLOSED · $code" to reason
        is WebSocketEvent.Failure -> "FAILED" to throwable.stackTraceToString()
        is WebSocketEvent.Connecting -> "CONNECTING" to url
        is WebSocketEvent.Cancelled -> "CANCELLED" to null
        is WebSocketEvent.TextMessage -> error("Handled separately")
    }

    private fun android.database.Cursor.longOrNull(index: Int): Long? =
        if (isNull(index)) null else getLong(index)

    private companion object {
        const val DATABASE_NAME = "websocket-recorder.db"
        const val DATABASE_VERSION = 2
        val listeners = CopyOnWriteArraySet<() -> Unit>()
        fun notifyChanged() = listeners.forEach { listener ->
            try { listener() } catch (_: Throwable) { }
        }
    }
}

internal data class StoredExchange(
    val id: Long,
    val uniqueId: String?,
    val type: String,
    val request: String?,
    val response: String?,
    val requestTimeMs: Long?,
    val responseTimeMs: Long?,
    val updatedTimeMs: Long,
) {
    val hasRequest: Boolean get() = request != null
    val hasResponse: Boolean get() = response != null
}
