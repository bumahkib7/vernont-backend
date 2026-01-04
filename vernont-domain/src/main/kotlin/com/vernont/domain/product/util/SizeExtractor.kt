package com.vernont.domain.product.util

/**
 * Utility for extracting size information from product titles.
 * Handles multiple formats: EU sizes, UK sizes, US sizes, generic numeric sizes.
 */
object SizeExtractor {

    private val SIZE_PATTERNS = listOf(
        // EU sizes: "Eu - 40", "EU 40", "EU-40", "Size 40"
        Regex("""\b(?:eu|size)\s*[-:]?\s*(\d{2}(?:\.\d)?)\b""", RegexOption.IGNORE_CASE) to SizeType.EU,

        // UK sizes: "UK 8", "UK-8"
        Regex("""\b(?:uk)\s*[-:]?\s*(\d{1,2}(?:\.\d)?)\b""", RegexOption.IGNORE_CASE) to SizeType.UK,

        // US sizes: "US 10", "US-10"
        Regex("""\b(?:us)\s*[-:]?\s*(\d{1,2}(?:\.\d)?)\b""", RegexOption.IGNORE_CASE) to SizeType.US,

        // Generic "Size: X" pattern
        Regex("""\bsize\s*[-:]?\s*(\d{1,2}(?:\.\d)?)\b""", RegexOption.IGNORE_CASE) to SizeType.NUMERIC,

        // Numeric sizes at end: "Brown 40", "Black 8.5"
        Regex("""[-\s](\d{1,2}(?:\.\d)?)\s*$""") to SizeType.NUMERIC
    )

    /**
     * Extracts size information from a product title.
     * @param title The product title to extract from
     * @return SizeInfo with extracted size and type, or null if no size found
     */
    fun extractSize(title: String): SizeInfo? {
        for ((pattern, type) in SIZE_PATTERNS) {
            val match = pattern.find(title)
            if (match != null) {
                val size = match.groupValues[1]
                return SizeInfo(size = size, type = type)
            }
        }
        return null
    }

    /**
     * Checks if a title likely contains size information
     */
    fun hasSize(title: String): Boolean {
        return SIZE_PATTERNS.any { (pattern, _) -> pattern.containsMatchIn(title) }
    }
}

/**
 * Extracted size information
 */
data class SizeInfo(
    val size: String,
    val type: SizeType
)

/**
 * Size type/unit
 */
enum class SizeType {
    EU,     // European size (e.g., 40, 42)
    UK,     // UK size (e.g., 8, 10)
    US,     // US size (e.g., 10, 12)
    NUMERIC // Generic numeric size
}
