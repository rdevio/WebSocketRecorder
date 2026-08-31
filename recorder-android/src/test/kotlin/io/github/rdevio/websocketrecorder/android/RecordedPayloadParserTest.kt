package io.github.rdevio.websocketrecorder.android

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RecordedPayloadParserTest {
    @Test
    fun `keeps raw non object payload`() {
        val parsed = RecordedPayloadParser.parse("[1, 2, 3]")

        assertEquals("Message", parsed.type)
        assertEquals(null, parsed.uniqueId)
        assertTrue(parsed.normalized.contains("1"))
    }

    @Test
    fun `finds metadata in nested heterogeneous payload`() {
        val parsed = RecordedPayloadParser.parse(
            "{\"data\":{\"request_id\":\"abc\",\"messageType\":23}}",
        )

        assertEquals("abc", parsed.uniqueId)
        assertEquals("23", parsed.type)
    }
}
