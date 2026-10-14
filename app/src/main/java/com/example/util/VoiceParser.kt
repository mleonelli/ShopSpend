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

    /**
     * Parses multiple items spoken together as a list (e.g. "Latte, uova, pane, farina"
     * or "Milk, eggs, bread and cheese").
     * Returns a list of ParsedVoiceItem.
     */
    fun parseList(input: String): List<ParsedVoiceItem> {
        val trimmed = input.trim()
        if (trimmed.isBlank()) return emptyList()

        // 1. Try splitting by comma, semicolon, newline
        var rawParts = trimmed.split(Regex("[,;\\n]+"))
            .map { it.trim() }
            .filter { it.isNotBlank() }

        // 2. If no commas, check if separated by " e " or " and "
        if (rawParts.size <= 1) {
            val andTokens = trimmed.split(Regex("\\s+(?:and|e|ed)\\s+", RegexOption.IGNORE_CASE))
                .map { it.trim() }
                .filter { it.isNotBlank() }
            if (andTokens.size > 1) {
                // Ensure the last part isn't just cents (e.g. "due euro e cinquanta")
                val lastWordNum = parseWordOrDigit(andTokens.last())
                val isPriceExpression = lastWordNum in 1..99 && andTokens.any { it.contains("euro", true) || it.contains("dollar", true) }
                if (!isPriceExpression) {
                    rawParts = andTokens
                }
            }
        }

        if (rawParts.size > 1) {
            val items = mutableListOf<ParsedVoiceItem>()
            for (part in rawParts) {
                val cleanedPart = part
                    .replace(Regex("^(?:and|e|ed|aggiungi|add|compra|buy)\\s+", RegexOption.IGNORE_CASE), "")
                    .replace(Regex("\\s+(?:and|e|ed)$", RegexOption.IGNORE_CASE), "")
                    .trim()
                if (cleanedPart.isNotBlank()) {
                    val parsed = parse(cleanedPart)
                    if (parsed != null && parsed.name.isNotBlank()) {
                        items.add(parsed)
                    }
                }
            }
            if (items.isNotEmpty()) return items
        }

        // Fallback to single item
        val single = parse(trimmed)
        return if (single != null && single.name.isNotBlank()) listOf(single) else emptyList()
    }

    /**
     * Parses only a price from speech, such as "1.50", "2,80", "due euro e cinquanta",
     * "three dollars", "one fifty".
     */
    fun parsePriceOnly(input: String): Double? {
        val trimmed = input.trim()
            .replace(",", ".")
            .replace(Regex("[$€£¥₹]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()

        // Direct number
        val directMatch = Regex("""^(\d+(?:\.\d{1,2})?)\s*(?:dollars?|bucks?|euros?|euro|cents?|centesimi?)?$""", RegexOption.IGNORE_CASE).find(trimmed)
        if (directMatch != null) {
            return directMatch.groupValues[1].toDoubleOrNull()
        }

        // Spoken currency
        val spoken = Regex("""^(\d+|[a-zàèéìòù']+)\s*(?:dollars?|bucks?|euros?|euro)\s*(?:and|e|ed)?\s*(\d+|[a-zàèéìòù']+)?\s*(?:cents?|centesimi?)?$""", RegexOption.IGNORE_CASE).find(trimmed)
        if (spoken != null) {
            val whole = parseWordOrDigit(spoken.groupValues[1])
            val cents = spoken.groupValues[2].takeIf { it.isNotBlank() }?.let { parseWordOrDigit(it) } ?: 0
            val price = whole + (cents / 100.0)
            if (price > 0.0) return price
        }

        // Spoken number words (e.g. "two fifty" or "due e cinquanta")
        val split = trimmed.split(" ")
        if (split.size == 2) {
            val whole = parseWordOrDigit(split[0])
            val cents = parseWordOrDigit(split[1])
            if (whole > 0 && cents in 1..99) return whole + (cents / 100.0)
            if (whole > 0) return whole.toDouble()
        } else if (split.size == 3 && (split[1].equals("e", true) || split[1].equals("and", true))) {
            val whole = parseWordOrDigit(split[0])
            val cents = parseWordOrDigit(split[2])
            if (whole > 0 && cents in 1..99) return whole + (cents / 100.0)
        }

        val singleWordNum = parseWordOrDigit(trimmed)
        if (singleWordNum > 0) return singleWordNum.toDouble()

        return null
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
