package dev.fabik.bluetoothhid.ocr

/**
 * Trained Dictionary and Fuzzy Matcher for Indian Two-Wheeler Spare Parts
 * (Honda, Hero MotoCorp, Bajaj, TVS, Castrol, Motul).
 *
 * Implements Levenshtein-based auto-correction for handwritten character confusions
 * (e.g. O -> 0, I/l -> 1, S -> 5, Z -> 2, missing hyphens).
 */
object AutomobileTrainedDictionary {

    // Fast-Moving Honda & Hero Genuine OEM Parts Catalog
    val KNOWN_OEM_PARTS: List<String> = listOf(
        // Honda Air Filters
        "17211-KUD-900", "17211-KV6-900", "17211-KWP-D00", "17211-KPL-900", "17211-KRM-860", "17211-KRP-980",
        // Hero Air Filters
        "17211-KTC-900", "17211-KTE-910", "17211-KTR-940", "17211-KWA-940", "17211-AAH-000", "17211-KCC-900",
        // Honda Drive Face & Variator
        "22102-KUD-900", "22102-KWP-D00", "22110-KUD-900", "22110-KWP-D00", "22011-KUD-900",
        // Honda Drive Belts (Activa, Dio, Aviator)
        "23100-KUD-901", "23100-KWP-D01", "23100-KVB-901", "23100-KPL-900",
        // Brake Shoes & Pads (Honda & Hero)
        "06455-KRE-G01", "06455-KPP-901", "06455-KSP-901", "06430-KUD-900", "06430-KTC-900", "06430-KTE-900", "06430-KRF-B00",
        // Clutch Plates & Discs
        "22201-KGF-900", "22201-MR8-000", "22201-166-000", "22201-KRM-840", "22201-KYJ-901",
        // Silencer & Exhaust
        "18350-AAH-000", "18350-KTC-900", "18350-KTR-940", "18300-KUD-900",
        // Electricals & Regulators
        "31600-KTR-941", "31600-KTC-900", "31600-KUD-900", "30500-KTC-900", "35010-KCC-900",
        // Oil Filters & Spark Plugs
        "15412-MGS-D21", "15410-MCJ-000", "98069-57916", "98069-58716", "98079-56846",
        // Control Cables
        "45450-KUD-900", "43450-KUD-900", "17910-KUD-900", "22870-KTC-900", "44830-KUD-900",
        // Drive Chains & Sprockets
        "40530-KTC-900", "41201-KTC-900", "23801-KTC-900", "40530-KTR-940",
        // Fork Oil Seals
        "51490-KRM-861", "51490-KTC-900", "51490-KUD-900",
        // Bajaj & TVS Common Parts
        "DV101048", "PA541212", "JA113000", "36JZ0001", "DK101340", "DJ181045", "N9041690", "M1200050",
        // Indian EAN-13 Fast Moving Barcodes (Castrol, Motul, Gulf, Servo)
        "8901030381734", "8901030381741", "8901030381727", "8901425000000", "8906001000000"
    )

    // Lookup index of unhyphenated codes to standardized hyphenated codes
    private val UNHYPHENATED_INDEX: Map<String, String> = buildMap {
        for (part in KNOWN_OEM_PARTS) {
            val unhyphen = part.replace("-", "").uppercase()
            put(unhyphen, part)
        }
    }

    /**
     * Checks if a candidate part code matches or can be auto-corrected to a known genuine OEM part.
     * Returns the verified OEM code if matched, or the normalized candidate otherwise.
     */
    fun matchOrAutoCorrect(candidate: String): String {
        val upper = candidate.uppercase().trim()

        // 1. Exact match in catalog
        if (KNOWN_OEM_PARTS.contains(upper)) {
            return upper
        }

        // 2. Unhyphenated match (e.g. "17211KUD900" -> "17211-KUD-900")
        val withoutHyphens = upper.replace("-", "")
        UNHYPHENATED_INDEX[withoutHyphens]?.let { return it }

        // 3. Normalized character confusion (e.g. O -> 0, I -> 1)
        val normalizedConfusions = normalizeConfusions(upper)
        if (KNOWN_OEM_PARTS.contains(normalizedConfusions)) {
            return normalizedConfusions
        }
        val normalizedUnhyphen = normalizedConfusions.replace("-", "")
        UNHYPHENATED_INDEX[normalizedUnhyphen]?.let { return it }

        // 4. Fuzzy Levenshtein match for Honda/Hero format (distance <= 2)
        if (upper.length in 11..16 && upper.contains("-")) {
            for (known in KNOWN_OEM_PARTS) {
                if (levenshteinDistance(upper, known) <= 2) {
                    return known
                }
            }
        }

        return upper
    }

    private fun normalizeConfusions(str: String): String {
        val sb = StringBuilder()
        for (c in str) {
            when (c) {
                'O', 'o' -> sb.append('0')
                'I', 'l', '|' -> sb.append('1')
                'Z', 'z' -> sb.append('2')
                'S', 's' -> sb.append('5')
                'B' -> sb.append('8')
                'G' -> sb.append('6')
                else -> sb.append(c)
            }
        }
        return sb.toString()
    }

    private fun levenshteinDistance(s1: String, s2: String): Int {
        val dp = Array(s1.length + 1) { IntArray(s2.length + 1) }
        for (i in 0..s1.length) dp[i][0] = i
        for (j in 0..s2.length) dp[0][j] = j

        for (i in 1..s1.length) {
            for (j in 1..s2.length) {
                val cost = if (s1[i - 1] == s2[j - 1]) 0 else 1
                dp[i][j] = minOf(
                    dp[i - 1][j] + 1,      // deletion
                    dp[i][j - 1] + 1,      // insertion
                    dp[i - 1][j - 1] + cost // substitution
                )
            }
        }
        return dp[s1.length][s2.length]
    }
}
