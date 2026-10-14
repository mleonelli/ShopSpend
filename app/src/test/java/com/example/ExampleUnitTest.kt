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
        assertEquals("Mele", parsed.name)
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

    @Test
    fun testUnpricedItem() {
        val parsed = VoiceParser.parse("Latte")
        assertNotNull(parsed)
        assertEquals("Latte", parsed?.name)
        assertEquals(0.0, parsed!!.price, 0.001)
        assertEquals(1, parsed.quantity)
    }

    @Test
    fun testListParsingItalian() {
        val list = VoiceParser.parseList("Pane, latte, uova")
        assertEquals(3, list.size)
        assertEquals("Pane", list[0].name)
        assertEquals("Latte", list[1].name)
        assertEquals("Uova", list[2].name)
        assertEquals(0.0, list[0].price, 0.001)
        assertEquals(0.0, list[1].price, 0.001)
        assertEquals(0.0, list[2].price, 0.001)
    }

    @Test
    fun testListParsingEnglish() {
        val list = VoiceParser.parseList("Bread, milk, eggs")
        assertEquals(3, list.size)
        assertEquals("Bread", list[0].name)
        assertEquals("Milk", list[1].name)
        assertEquals("Eggs", list[2].name)
    }

    @Test
    fun testPriceOnlyParsing() {
        val price1 = VoiceParser.parsePriceOnly("1.50")
        assertEquals(1.50, price1 ?: 0.0, 0.001)

        val price2 = VoiceParser.parsePriceOnly("due euro e cinquanta")
        assertEquals(2.50, price2 ?: 0.0, 0.001)

        val price3 = VoiceParser.parsePriceOnly("three fifty")
        assertEquals(3.50, price3 ?: 0.0, 0.001)
    }
}

