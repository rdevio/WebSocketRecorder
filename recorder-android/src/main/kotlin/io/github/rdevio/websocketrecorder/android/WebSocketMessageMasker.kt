package io.github.rdevio.websocketrecorder.android

/**
 * Controls the title and visibility of a captured text message.
 *
 * Return a replacement title to keep the message, or `null` to discard it before persistence.
 */
fun interface WebSocketMessageMasker {
    fun displayType(type: String): String?

    companion object {
        @JvmField
        val KEEP_ALL = WebSocketMessageMasker { it }
    }
}

/**
 * Convenience masker for protocols whose message type is an integer code.
 */
class TypeCodeMasker(
    private val labels: Map<Int, String>,
    private val ignoredTypes: Set<Int> = emptySet(),
) : WebSocketMessageMasker {
    override fun displayType(type: String): String? {
        val code = type.toIntOrNull() ?: return type
        if (code in ignoredTypes) return null
        return labels[code] ?: type
    }
}
