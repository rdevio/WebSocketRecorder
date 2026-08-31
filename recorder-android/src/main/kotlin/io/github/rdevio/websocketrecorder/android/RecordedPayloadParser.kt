package io.github.rdevio.websocketrecorder.android

import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener

internal data class ParsedPayload(
    val uniqueId: String?,
    val type: String,
    val normalized: String,
)

internal object RecordedPayloadParser {
    fun parse(raw: String): ParsedPayload {
        val root = parseJson(raw)
        val normalizedRoot = decodeRecursively(root)
        val uniqueId = findValue(normalizedRoot, "uniqueId", "unique_id", "requestId", "request_id")
        val type = findValue(normalizedRoot, "type", "messageType", "message_type", "event")
            ?: "Message"

        return ParsedPayload(
            uniqueId = uniqueId,
            type = type,
            normalized = when (normalizedRoot) {
                is JSONObject -> normalizedRoot.toString(2)
                is JSONArray -> normalizedRoot.toString(2)
                else -> raw
            },
        )
    }

    private fun decodeRecursively(value: Any?): Any? = when (value) {
        is String -> {
            val decoded = parseJson(value)
            if (decoded == value) value else decodeRecursively(decoded)
        }
        is JSONObject -> {
            val keys = value.keys().asSequence().toList()
            keys.forEach { key -> value.put(key, decodeRecursively(value.opt(key))) }
            value
        }
        is JSONArray -> {
            for (index in 0 until value.length()) {
                value.put(index, decodeRecursively(value.opt(index)))
            }
            value
        }
        else -> value
    }

    private fun findValue(value: Any?, vararg names: String): String? {
        when (value) {
            is JSONObject -> {
                for (name in names) {
                    val direct = value.optValueOrNull(name)
                    if (direct != null) return direct
                }
                val keys = value.keys()
                while (keys.hasNext()) {
                    val nested = findValue(value.opt(keys.next()), *names)
                    if (nested != null) return nested
                }
            }
            is JSONArray -> for (index in 0 until value.length()) {
                val nested = findValue(value.opt(index), *names)
                if (nested != null) return nested
            }
        }
        return null
    }

    private fun parseJson(value: String): Any {
        val trimmed = value.trim()
        if (trimmed.isEmpty()) return value
        return try {
            JSONTokener(trimmed).nextValue()
        } catch (_: Throwable) {
            value
        }
    }

    private fun JSONObject.optStringOrNull(key: String): String? =
        if (has(key) && !isNull(key)) optString(key).takeIf { it.isNotBlank() } else null

    private fun JSONObject.optValueOrNull(key: String): String? =
        if (has(key) && !isNull(key)) opt(key)?.toString()?.takeIf { it.isNotBlank() } else null
}
