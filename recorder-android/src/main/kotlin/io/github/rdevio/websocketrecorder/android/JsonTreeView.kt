package io.github.rdevio.websocketrecorder.android

import android.content.Context
import android.graphics.Typeface
import android.os.Build
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener

internal class JsonTreeView(context: Context) : LinearLayout(context) {

    init {
        orientation = VERTICAL
    }

    fun setPayload(payload: String) {
        removeAllViews()
        val parsed = parseContainer(payload)
        if (parsed == null) {
            addView(plainText(JsonPrettyPrinter.format(payload)))
        } else {
            addNode(this, null, parsed, depth = 0, initiallyExpanded = true)
        }
    }

    private fun addNode(
        parent: LinearLayout,
        key: String?,
        value: Any?,
        depth: Int,
        initiallyExpanded: Boolean = false,
    ) {
        if (value !is JSONObject && value !is JSONArray) {
            parent.addView(primitiveRow(key, value, depth))
            return
        }

        val childCount = when (value) {
            is JSONObject -> value.length()
            is JSONArray -> value.length()
            else -> 0
        }
        val node = LinearLayout(context).apply { orientation = VERTICAL }
        val children = LinearLayout(context).apply {
            orientation = VERTICAL
            visibility = if (initiallyExpanded) View.VISIBLE else View.GONE
        }
        var expanded = initiallyExpanded
        var childrenCreated = false
        val header = TextView(context).apply {
            typeface = Typeface.MONOSPACE
            textSize = 14f
            setPadding(indent(depth), dp(7), dp(8), dp(7))
            setBackgroundColor(color(R.color.websocket_recorder_json_container_background))
        }

        fun populateChildren() {
            if (childrenCreated) return
            childrenCreated = true
            when (value) {
                is JSONObject -> value.keys().forEach { childKey ->
                    addNode(children, childKey, value.opt(childKey), depth + 1)
                }
                is JSONArray -> repeat(value.length()) { index ->
                    addNode(children, "[$index]", value.opt(index), depth + 1)
                }
            }
        }

        fun updateHeader() {
            header.text = containerLabel(key, value, childCount, expanded)
        }

        if (initiallyExpanded) populateChildren()
        updateHeader()
        if (childCount > 0) {
            header.setOnClickListener {
                expanded = !expanded
                if (expanded) populateChildren()
                children.visibility = if (expanded) View.VISIBLE else View.GONE
                updateHeader()
            }
        }
        node.addView(header)
        node.addView(children)
        parent.addView(node)
    }

    private fun primitiveRow(key: String?, value: Any?, depth: Int) = TextView(context).apply {
        typeface = Typeface.MONOSPACE
        textSize = 14f
        setTextIsSelectable(true)
        setPadding(indent(depth), dp(5), dp(8), dp(5))
        text = styledPrimitive(key, value)
    }

    private fun containerLabel(
        key: String?,
        value: Any,
        childCount: Int,
        expanded: Boolean,
    ): CharSequence = SpannableStringBuilder().apply {
        append(if (childCount == 0) "  " else if (expanded) "− " else "+ ")
        appendKey(key)
        val delimiters = if (value is JSONObject) "{}" else "[]"
        val summary = "${delimiters.first()} $childCount ${delimiters.last()}"
        val start = length
        append(summary)
        setSpan(
            StyleSpan(Typeface.BOLD),
            start,
            length,
            Spannable.SPAN_EXCLUSIVE_EXCLUSIVE,
        )
    }

    private fun styledPrimitive(key: String?, value: Any?): CharSequence =
        SpannableStringBuilder().apply {
            append("  ")
            appendKey(key)
            val displayValue: String
            val valueColor: Int
            when (value) {
                null, JSONObject.NULL -> {
                    displayValue = "null"
                    valueColor = color(R.color.websocket_recorder_json_null)
                }
                is String -> {
                    displayValue = JSONObject.quote(value)
                    valueColor = color(R.color.websocket_recorder_json_string)
                }
                is Number -> {
                    displayValue = value.toString()
                    valueColor = color(R.color.websocket_recorder_json_number)
                }
                is Boolean -> {
                    displayValue = value.toString()
                    valueColor = color(R.color.websocket_recorder_json_boolean)
                }
                else -> {
                    displayValue = value.toString()
                    valueColor = color(R.color.websocket_recorder_json_string)
                }
            }
            val start = length
            append(displayValue)
            setSpan(
                ForegroundColorSpan(valueColor),
                start,
                length,
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE,
            )
        }

    private fun SpannableStringBuilder.appendKey(key: String?) {
        if (key == null) return
        val start = length
        append(if (key.startsWith("[") && key.endsWith("]")) key else JSONObject.quote(key))
        setSpan(
            ForegroundColorSpan(color(R.color.websocket_recorder_json_key)),
            start,
            length,
            Spannable.SPAN_EXCLUSIVE_EXCLUSIVE,
        )
        setSpan(
            StyleSpan(Typeface.BOLD),
            start,
            length,
            Spannable.SPAN_EXCLUSIVE_EXCLUSIVE,
        )
        append(": ")
    }

    private fun plainText(value: String) = TextView(context).apply {
        text = value
        typeface = Typeface.MONOSPACE
        setTextIsSelectable(true)
        setPadding(dp(8), dp(8), dp(8), dp(24))
    }

    private fun parseContainer(raw: String): Any? {
        var candidate: Any = raw.trim()
        repeat(4) {
            val text = candidate as? String ?: return@repeat
            candidate = try {
                JSONTokener(text).nextValue()
            } catch (_: Throwable) {
                return null
            }
        }
        return candidate.takeIf { it is JSONObject || it is JSONArray }
    }

    private fun indent(depth: Int): Int = dp(8 + depth.coerceAtMost(10) * 14)

    @Suppress("DEPRECATION")
    private fun color(resourceId: Int): Int =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            resources.getColor(resourceId, context.theme)
        } else {
            resources.getColor(resourceId)
        }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}
