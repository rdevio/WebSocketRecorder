package io.github.websocketrecorder.android

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
        if (root !is JSONObject) {
            return ParsedPayload(null, "Message", raw)
        }

        val decodedContent = decodeRecursively(root.opt("content"))
        if (decodedContent !== root.opt("content") && decodedContent != null) {
            root.put("content", decodedContent)
        }

        val contentObject = decodedContent as? JSONObject
        val uniqueId = root.optStringOrNull("uniqueId")
            ?: contentObject?.optStringOrNull("uniqueId")
        val type = contentObject?.optValueOrNull("type")
            ?: root.optValueOrNull("type")
            ?: "Message"

        return ParsedPayload(
            uniqueId = uniqueId,
            type = type,
            normalized = root.toString(2),
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
