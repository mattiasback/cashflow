package app.cashflow.core.categorization

/**
 * Turns bank text into a stable payee. Card texts look like `MERCHANT,City,SE`, often with
 * a card-processor prefix (`SumUp  *`, `Zettle_*`, `K*`) and a store number. Merchant
 * memory keys on the result, so "ICA NARA X 1234,STAD,SE" and "ICA NARA X,STAD,SE" meet.
 */
class PayeeNormalizer {

    fun normalize(raw: String): String {
        var s = raw.trim()
        // Card-processor prefixes.
        s = processorPrefix.replace(s, "")
        // ",City,SE" / ",City,SWE" suffixes from card terminals.
        s = cityCountrySuffix.replace(s, "")
        // Trailing reference numbers and store numbers ("#1234", "1234", "26-10-12").
        s = trailingDate.replace(s, "")
        s = trailingNumber.replace(s, "")
        s = s.replace(multiSpace, " ").trim().trimEnd(',', '.', '-', '*', '/').trim()
        return if (s.isEmpty()) raw.trim() else s
    }

    /** Case-insensitive key for merchant memory and rules. */
    fun key(raw: String): String = normalize(raw).uppercase()

    /** A readable name for lists: all-caps bank text becomes title case. */
    fun displayName(raw: String): String {
        val n = normalize(raw)
        val letters = n.filter(Char::isLetter)
        if (letters.isEmpty() || letters.any(Char::isLowerCase)) return n
        return n.lowercase().split(' ').joinToString(" ") { word ->
            if (word.length <= 2 && word.all(Char::isLetter) && word in keepUpper) word.uppercase()
            else word.replaceFirstChar(Char::titlecase)
        }
    }

    private companion object {
        val processorPrefix = Regex("""^(?i)(sumup\s*\*|zettle_\*|izettle\*|iz\s*\*|k\*|sq\s*\*|paypal\s*\*|klarna\s*\*|google\s*\*|apple\.com/bill\s*)\s*""")
        val cityCountrySuffix = Regex(""",[^,]*,\s*[A-Z]{2,3}\s*$""")
        val trailingDate = Regex("""\s+\d{2}-\d{2}-\d{2}$""")
        val trailingNumber = Regex("""\s+#?\d{2,}$""")
        val multiSpace = Regex("""\s+""")
        val keepUpper = setOf("ab", "hb", "kb", "sj", "sl", "h&m")
    }
}
