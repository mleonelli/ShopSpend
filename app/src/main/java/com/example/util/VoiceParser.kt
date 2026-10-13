package com.example.util

import java.util.Locale

data class ParsedVoiceItem(
    val name: String,
    val price: Double,
    val quantity: Int = 1,
    val originalText: String = ""
)

object VoiceParser {

    private val numberWords = mapOf(
        "zero" to 0,
        // English
        "a" to 1, "an" to 1, "one" to 1, "two" to 2, "three" to 3,
        "four" to 4, "five" to 5, "six" to 6, "seven" to 7, "eight" to 8,
        "nine" to 9, "ten" to 10, "eleven" to 11, "twelve" to 12, "thirteen" to 13,
        "fourteen" to 14, "fifteen" to 15, "sixteen" to 16, "seventeen" to 17,
        "eighteen" to 18, "nineteen" to 19, "twenty" to 20, "thirty" to 30,
        "forty" to 40, "fifty" to 50, "sixty" to 60, "seventy" to 70,
        "eighty" to 80, "ninety" to 90, "hundred" to 100,

        // Italian
        "un" to 1, "uno" to 1, "una" to 1, "un'" to 1, "due" to 2, "tre" to 3, "tré" to 3,
        "quattro" to 4, "cinque" to 5, "sei" to 6, "sette" to 7, "otto" to 8, "nove" to 9,
        "dieci" to 10, "undici" to 11, "dodici" to 12, "tredici" to 13, "quattordici" to 14,
        "quindici" to 15, "sedici" to 16, "diciassette" to 17, "diciotto" to 18, "diciannove" to 19,
        "venti" to 20, "ventuno" to 21, "ventidue" to 22, "ventitre" to 23, "ventitré" to 23,
        "ventiquattro" to 24, "venticinque" to 25, "ventisei" to 26, "ventisette" to 27,
        "ventotto" to 28, "ventinove" to 29,
        "trenta" to 30, "trentuno" to 31, "trentadue" to 32, "trentatre" to 33, "trentatré" to 33,
        "trentaquattro" to 34, "trentacinque" to 35, "trentasei" to 36, "trentasette" to 37,
        "trentotto" to 38, "trentanove" to 39,
        "quaranta" to 40, "quarantuno" to 41, "quarantadue" to 42, "quarantatre" to 43,
        "quarantatré" to 43, "quarantacinque" to 45, "quarantotto" to 48,
        "cinquanta" to 50, "cinquantuno" to 51, "cinquantacinque" to 55,
        "sessanta" to 60, "sessantacinque" to 65,
        "settanta" to 70, "settantacinque" to 75,
        "ottanta" to 80, "ottantacinque" to 85,
        "novanta" to 90, "novantacinque" to 95, "novantanove" to 99,
        "cento" to 100
    )

    private val connectingWordsRegex =
        Regex("""\s+(?:for|at|costs?|costing|is|per|a|costa|costano|è|sono)\s*$""", RegexOption.IGNORE_CASE)

    /**
     * Parses spoken phrases in both English and Italian, such as:
     * - "Milk 2.50", "Latte 2,50", "3 Apples for $4.99", "3 mele a 4.99"
     * - "Bread two fifty", "Pane due euro e cinquanta", "2 pacchi di pasta 1.80"
     */
    fun parse(input: String): ParsedVoiceItem? {
        val trimmed = input.trim()
        if (trimmed.isBlank()) return null

        val cleaned = trimmed
            .replace(",", ".")
            .replace(Regex("[$€£¥₹]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()

        // 1. Try regex pattern with explicit numeric price (e.g. "Milk 2.50", "Latte 2.50", "3 Mele a 4.99")
        val decimalMatch = Regex(
            """^(.*?)\s+(\d+(?:\.\d{1,2})?)\s*(?:dollars?|bucks?|euros?|euro|pounds?|sterline?|cents?|centesimi?)?$""",
            RegexOption.IGNORE_CASE
        ).find(cleaned)

        if (decimalMatch != null) {
            val rawName = decimalMatch.groupValues[1]
                .replace(connectingWordsRegex, "")
                .trim()
            val priceStr = decimalMatch.groupValues[2]
            val price = priceStr.toDoubleOrNull() ?: 0.0

            val (qty, name) = extractLeadingQuantity(rawName)
            if (name.isNotBlank() && price > 0.0) {
                return ParsedVoiceItem(name = formatName(name), price = price, quantity = qty, originalText = trimmed)
            }
        }

        // 2. Try phrase with spoken currency: e.g. "Apples 3 dollars and 50 cents" or "Pane due euro e cinquanta"
        val spokenCurrencyPattern = Regex(
            """^(.*?)\s+(\d+|[a-zàèéìòù']+)\s*(?:dollars?|bucks?|euros?|euro|pounds?|sterline?)\s*(?:and|e|ed)?\s*(\d+|[a-zàèéìòù']+)?\s*(?:cents?|centesimi?)?$""",
            RegexOption.IGNORE_CASE
        ).find(cleaned)

        if (spokenCurrencyPattern != null) {
            val rawName = spokenCurrencyPattern.groupValues[1]
                .replace(connectingWordsRegex, "")
                .trim()
            val wholePartStr = spokenCurrencyPattern.groupValues[2]
            val centsPartStr = spokenCurrencyPattern.groupValues[3].takeIf { it.isNotBlank() }

            val whole = parseWordOrDigit(wholePartStr)
            val cents = if (centsPartStr != null) parseWordOrDigit(centsPartStr) else 0

            val price = whole + (cents / 100.0)
            val (qty, name) = extractLeadingQuantity(rawName)
            if (name.isNotBlank() && price > 0.0) {
                return ParsedVoiceItem(name = formatName(name), price = price, quantity = qty, originalText = trimmed)
            }
        }

        // 3. Try spoken phrase with "X and Y" or "X e Y" or "X Y" (e.g. "Latte due e cinquanta", "Bread two fifty")
        val splitWords = cleaned.split(" ")
        if (splitWords.size >= 3 && (splitWords[splitWords.size - 2].equals("and", ignoreCase = true) || splitWords[splitWords.size - 2].equals("e", ignoreCase = true))) {
            val wholeNum = parseWordOrDigit(splitWords[splitWords.size - 3])
            val lastNum = parseWordOrDigit(splitWords.last())
            if (wholeNum > 0 && lastNum in 1..99) {
                val rawName = splitWords.take(splitWords.size - 3).joinToString(" ")
                    .replace(connectingWordsRegex, "")
                    .trim()
                val price = wholeNum + (lastNum / 100.0)
                val (qty, name) = extractLeadingQuantity(rawName)
                if (name.isNotBlank()) {
                    return ParsedVoiceItem(name = formatName(name), price = price, quantity = qty, originalText = trimmed)
                }
            }
        }

        // 4. Try "Item X Y" format like "Bread 2 50" or "Milk two fifty"
        if (splitWords.size >= 2) {
            val lastWord = splitWords.last()
            val lastNum = parseWordOrDigit(lastWord)

            if (splitWords.size >= 3) {
                val secondLast = splitWords[splitWords.size - 2]
                val wholeNum = parseWordOrDigit(secondLast)
                if (wholeNum > 0 && lastNum in 1..99) {
                    val rawName = splitWords.take(splitWords.size - 2).joinToString(" ")
                        .replace(connectingWordsRegex, "")
                        .trim()
                    val price = wholeNum + (lastNum / 100.0)
                    val (qty, name) = extractLeadingQuantity(rawName)
                    if (name.isNotBlank()) {
                        return ParsedVoiceItem(name = formatName(name), price = price, quantity = qty, originalText = trimmed)
                    }
                }
            }

            // Single last word as price
            val singlePrice = lastWord.toDoubleOrNull() ?: lastNum.toDouble()
            if (singlePrice > 0.0) {
                val rawName = splitWords.take(splitWords.size - 1).joinToString(" ")
                    .replace(connectingWordsRegex, "")
                    .trim()
                val (qty, name) = extractLeadingQuantity(rawName)
                if (name.isNotBlank()) {
                    return ParsedVoiceItem(name = formatName(name), price = singlePrice, quantity = qty, originalText = trimmed)
                }
            }
        }

        // Fallback: If price cannot be parsed, treat as item name with default 0.0 price
        val (qty, name) = extractLeadingQuantity(cleaned)
        return ParsedVoiceItem(
            name = formatName(name.ifBlank { cleaned }),
            price = 0.0,
            quantity = qty,
            originalText = trimmed
        )
    }

    private fun extractLeadingQuantity(rawName: String): Pair<Int, String> {
        var name = rawName.trim()
        val tokens = name.split(" ")
        if (tokens.size > 1) {
            val first = tokens[0].lowercase(Locale.ROOT)
            val qty = first.toIntOrNull() ?: numberWords[first]
            if (qty != null && qty in 1..99) {
                val rest = tokens.drop(1).joinToString(" ")
                    .replace(
                        Regex(
                            "^(?:items?|pieces?|bottles?|cans?|packs?|boxes?|bags?|cartons?|of|articoli|pezzi|bottiglie|lattine|pacchi|pacchetto|pacchetti|scatole|scatola|sacchetti|sacchetto|cartoni|chili|kg|etti|etto|di)\\s+",
                            RegexOption.IGNORE_CASE
                        ),
                        ""
                    )
                return Pair(qty, rest)
            }
        }
        return Pair(1, name)
    }

    private fun parseWordOrDigit(token: String): Int {
        val lower = token.lowercase(Locale.ROOT).trim()
        lower.toIntOrNull()?.let { return it }

        // check word match
        numberWords[lower]?.let { return it }

        // composite like "twenty-five" or "thirty five"
        val parts = lower.split("-", " ")
        if (parts.size == 2) {
            val tens = numberWords[parts[0]] ?: 0
            val ones = numberWords[parts[1]] ?: 0
            if (tens > 0 || ones > 0) return tens + ones
        }

        return 0
    }

    private fun formatName(name: String): String {
        return name
            .trim()
            .replace(Regex("^(?:add|buy|get|put|insert|aggiungi|compra|prendi|metti|inserisci)\\s+", RegexOption.IGNORE_CASE), "")
            .replace(Regex("\\s+"), " ")
            .trim()
            .replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.ROOT) else it.toString() }
    }
}
