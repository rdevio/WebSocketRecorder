package io.github.websocketrecorder.android

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class TypeCodeMaskerTest {
    private val masker = TypeCodeMasker(
        labels = mapOf(23 to "GET_USER_PROFILE"),
        ignoredTypes = setOf(99),
    )

    @Test
    fun `maps a known numeric type`() {
        assertEquals("GET_USER_PROFILE", masker.displayType("23"))
    }

    @Test
    fun `keeps an unknown type`() {
        assertEquals("42", masker.displayType("42"))
        assertEquals("MESSAGE", masker.displayType("MESSAGE"))
    }

    @Test
    fun `returns null for an ignored type`() {
        assertNull(masker.displayType("99"))
    }
}
