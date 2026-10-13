package com.example

import com.example.util.VoiceParser
import org.junit.Assert.*
import org.junit.Test

class ExampleUnitTest {
    @Test
    fun testEnglishVoiceParsing() {
        val parsed = VoiceParser.parse("Milk 2.50")
        assertNotNull(parsed)
        assertEquals("Milk", parsed?.name)
        assertEquals(2.50, parsed!!.price, 0.001)
        assertEquals(1, parsed.quantity)
    }

    @Test
    fun testItalianVoiceParsingWithComma() {
        val parsed = VoiceParser.parse("Latte 2,50")
        assertNotNull(parsed)
        assertEquals("Latte", parsed?.name)
        assertEquals(2.50, parsed!!.price, 0.001)
        assertEquals(1, parsed.quantity)
    }

    @Test
    fun testItalianQuantityAndConnector() {
        val parsed = VoiceParser.parse("3 mele a 4.99")
        assertNotNull(parsed)
        assertEquals(3, parsed!!.quantity)
        assertEquals("mele", parsed.name)
        assertEquals(4.99, parsed.price, 0.001)
    }

    @Test
    fun testItalianWordNumbers() {
        val parsed = VoiceParser.parse("Pane due euro e cinquanta")
        assertNotNull(parsed)
        assertEquals("Pane", parsed?.name)
        assertEquals(2.50, parsed!!.price, 0.001)
    }

    @Test
    fun testEnglishWordNumbers() {
        val parsed = VoiceParser.parse("Coffee three fifty")
        assertNotNull(parsed)
        assertEquals("Coffee", parsed?.name)
        assertEquals(3.50, parsed!!.price, 0.001)
    }
}

