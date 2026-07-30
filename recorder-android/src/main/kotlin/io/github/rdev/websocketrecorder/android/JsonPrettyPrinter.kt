package io.github.rdev.websocketrecorder.android

import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener

internal object JsonPrettyPrinter {
    fun format(value: String): String {
        val trimmed = value.trim()
        if (trimmed.isEmpty()) return value

        return try {
            when (val json = JSONTokener(trimmed).nextValue()) {
                is JSONObject -> json.toString(INDENT_SPACES)
                is JSONArray -> json.toString(INDENT_SPACES)
                else -> value
            }
        } catch (_: Throwable) {
            value
        }
    }

    private const val INDENT_SPACES = 2
}
