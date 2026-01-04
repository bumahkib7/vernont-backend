package com.vernont.application.product

/**
 * Allowed fashion categories for the public price-comparison catalog.
 * Keep these names in sync with storefront display labels.
 */
object FashionCatalog {
    val allowedCategoryNames: List<String> = listOf(
        "men clothing",
        "women clothing",
        "bags",
        "perfumes",
        "jewelry",
        "clothing",
        "clothing accessories",
        "handbag & wallet accessories",
        "handbags, wallets & cases",
        "costumes & accessories",
        "apparel & accessories"
    )

    private val aliasToName: Map<String, String> = mapOf(
        "men" to "men clothing",
        "men-clothing" to "men clothing",
        "women" to "women clothing",
        "women-clothing" to "women clothing",
        "bag" to "bags",
        "bags" to "bags",
        "perfume" to "perfumes",
        "perfumes" to "perfumes",
        "jewelry" to "jewelry",
        "jewellery" to "jewelry",
        "apparel" to "apparel & accessories",
        "apparel-accessories" to "apparel & accessories",
        "handbags-wallets-cases" to "handbags, wallets & cases",
        "handbag-wallet-accessories" to "handbag & wallet accessories",
        "clothing-accessories" to "clothing accessories",
        "costumes-accessories" to "costumes & accessories"
    )

    fun normalize(name: String?): String? = name?.trim()?.lowercase()?.takeIf { it.isNotEmpty() }

    fun toSlug(name: String): String =
        name.lowercase()
            .replace(Regex("[^a-z0-9]+"), "-")
            .trim('-')

    fun resolveCategory(input: String?): String? {
        val normalized = normalize(input) ?: return null
        aliasToName[normalized]?.let { return it }
        aliasToName[toSlug(normalized)]?.let { return it }
        if (allowedCategoryNames.contains(normalized)) return normalized
        val slugMatch = allowedCategoryNames.firstOrNull { toSlug(it) == normalized }
        return slugMatch
    }

    fun isAllowed(name: String?): Boolean = resolveCategory(name) != null
}
