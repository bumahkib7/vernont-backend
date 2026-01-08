package com.vernont.api.seeder

import com.vernont.domain.fulfillment.*
import com.vernont.domain.inventory.*
import com.vernont.domain.product.*
import com.vernont.domain.region.*
import com.vernont.domain.store.*
import com.vernont.repository.fulfillment.*
import com.vernont.repository.inventory.*
import com.vernont.repository.product.*
import com.vernont.repository.region.*
import com.vernont.repository.store.*
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.runBlocking
import org.springframework.boot.CommandLineRunner
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal

private val logger = KotlinLogging.logger {}

@Component
class DemoDataSeeder(
    private val storeRepository: StoreRepository,
    private val salesChannelRepository: SalesChannelRepository,
    private val storeCurrencyRepository: StoreCurrencyRepository,
    private val regionRepository: RegionRepository,
    private val countryRepository: CountryRepository,
    private val stockLocationRepository: StockLocationRepository,
    private val fulfillmentProviderRepository: FulfillmentProviderRepository,
    private val shippingProfileRepository: ShippingProfileRepository,
    private val shippingOptionRepository: ShippingOptionRepository,
    private val productCategoryRepository: ProductCategoryRepository,
    private val productRepository: ProductRepository,
    private val productVariantRepository: ProductVariantRepository,
    private val inventoryItemRepository: InventoryItemRepository,
    private val inventoryLevelRepository: InventoryLevelRepository,
    private val currencyRepository: com.vernont.repository.region.CurrencyRepository,
    private val productCollectionRepository: ProductCollectionRepository,
    private val productTypeRepository: ProductTypeRepository,
    private val brandRepository: BrandRepository,
    private val productTagRepository: ProductTagRepository
) : CommandLineRunner {

    override fun run(vararg args: String) = runBlocking {
        val seedingEnabled = System.getenv("DEMO_SEED")?.equals("true", ignoreCase = true) == true
        if (!seedingEnabled) {
            logger.info { "Demo data seeding disabled (set DEMO_SEED=true to enable)." }
            return@runBlocking
        }

        // Check if demo fragrances already exist
        val demoFragranceHandles = listOf(
            "midnight-rose", "golden-amber", "velvet-oud", "jasmine-dreams",
            "crystal-iris", "noir-essence", "silk-magnolia", "royal-sandalwood",
            "ocean-breeze", "vanilla-orchid", "bergamot-bliss", "spiced-woods"
        )
        val existingDemoProducts = demoFragranceHandles.count { handle ->
            productRepository.findByHandle(handle) != null
        }

        if (existingDemoProducts == demoFragranceHandles.size) {
            logger.info { "Demo fragrance data already seeded (all products exist). Skipping." }
            return@runBlocking
        }

        logger.info { "Starting Vernont fragrance demo data seeding..." }

        try {
            seedCurrencies()
            seedStoreAndSalesChannel()
            val region = seedRegion()
            val stockLocation = seedStockLocation()
            val shippingProfile = seedFulfillment(region, stockLocation)

            // Seed product entities
            val productType = seedProductType()
            val brand = seedBrand()
            val categories = seedFragranceCategories()
            val collections = seedCollections()
            val tags = seedTags()

            seedFragrances(categories, collections, tags, productType, brand, shippingProfile, region, stockLocation)

            logger.info { "Vernont fragrance demo data seeding completed successfully." }
        } catch (e: Exception) {
            logger.error(e) { "Failed to seed demo data" }
        }
    }

    @Transactional
    protected fun seedCurrencies() {
        val currencies = listOf(
            Triple("gbp", "£", "British Pound"),
            Triple("eur", "€", "Euro"),
            Triple("usd", "$", "US Dollar"),
            Triple("aed", "د.إ", "UAE Dirham"),
            Triple("chf", "CHF", "Swiss Franc")
        )

        currencies.forEach { (code, symbol, name) ->
            if (currencyRepository.findByCode(code) == null) {
                Currency().apply {
                    this.code = code
                    this.symbol = symbol
                    this.symbolNative = symbol
                    this.name = name
                    this.decimalDigits = 2
                    this.rounding = 0.0
                    this.includesTax = code == "gbp" // UK includes VAT in prices
                }.also {
                    currencyRepository.save(it)
                    logger.info { "Created currency: $code" }
                }
            }
        }
    }

    @Transactional
    protected fun seedStoreAndSalesChannel() {
        // Ensure Vernont store exists
        val store = storeRepository.findAll().firstOrNull() ?: Store().apply {
            name = "Vernont"
            defaultCurrencyCode = "gbp"
            swapLinkTemplate = "https://vernont.com/swap={swap_id}"
            paymentLinkTemplate = "https://vernont.com/payment={payment_id}"
            inviteLinkTemplate = "https://vernont.com/invite={invite_id}"
        }.also {
            storeRepository.save(it)
            logger.info { "Created Vernont store" }
        }

        // Ensure default sales channel exists
        salesChannelRepository.findByName("Vernont Online") ?: SalesChannel().apply {
            name = "Vernont Online"
            description = "Vernont Luxury Fragrances Online Store"
            this.store = store
        }.also { salesChannelRepository.save(it) }

        // Update store currencies - GBP as default (UK-based)
        val existingCurrencies = storeCurrencyRepository.findByStoreId(store.id)

        if (existingCurrencies.none { it.currencyCode == "gbp" }) {
            StoreCurrency().apply {
                currencyCode = "gbp"
                isDefault = true
                this.store = store
            }.also { storeCurrencyRepository.save(it) }
        }

        if (existingCurrencies.none { it.currencyCode == "eur" }) {
            StoreCurrency().apply {
                currencyCode = "eur"
                isDefault = false
                this.store = store
            }.also { storeCurrencyRepository.save(it) }
        }

        if (existingCurrencies.none { it.currencyCode == "usd" }) {
            StoreCurrency().apply {
                currencyCode = "usd"
                isDefault = false
                this.store = store
            }.also { storeCurrencyRepository.save(it) }
        }
    }

    @Transactional
    protected fun seedRegion(): Region {
        val existingRegion = regionRepository.findByName("United Kingdom & Europe")
        if (existingRegion != null) {
            logger.info { "UK & Europe region already exists, skipping" }
            return existingRegion
        }

        val countries = countryRepository.findAllById(listOf("GB", "DE", "FR", "ES", "IT", "NL", "BE", "AT", "CH", "SE", "DK", "NO", "IE"))

        return Region().apply {
            name = "United Kingdom & Europe"
            currencyCode = "gbp"
            taxRate = BigDecimal("0.20") // 20% UK VAT
            this.countries.addAll(countries)
        }.also {
            regionRepository.save(it)
            logger.info { "Created UK & Europe region with ${countries.size} countries" }
        }
    }

    @Transactional
    protected fun seedStockLocation(): StockLocation {
        val existingLocation = stockLocationRepository.findByName("Vernont UK Warehouse")
        if (existingLocation != null) {
            logger.info { "Vernont UK Warehouse already exists, skipping" }
            return existingLocation
        }

        return StockLocation().apply {
            name = "Vernont UK Warehouse"
            address1 = "123 Perfume Lane"
            city = "London"
            countryCode = "GB"
        }.also {
            stockLocationRepository.save(it)
            logger.info { "Created Vernont UK Warehouse stock location" }
        }
    }

    @Transactional
    protected fun seedFulfillment(region: Region, stockLocation: StockLocation): ShippingProfile {
        // Shipping Profile for Fragrances
        val profile = shippingProfileRepository.findByName("Fragrance Shipping") ?: ShippingProfile().apply {
            name = "Fragrance Shipping"
            type = ShippingProfileType.DEFAULT
        }.also {
            shippingProfileRepository.save(it)
            logger.info { "Created Fragrance Shipping Profile" }
        }

        // Fulfillment Providers for UK/International shipping
        val providers = listOf(
            Triple("royal_mail", "Royal Mail", mutableMapOf<String, Any>(
                "tracking_url" to "https://www.royalmail.com/track-your-item#/tracking-results/",
                "services" to listOf("tracked24", "tracked48", "special_delivery_9am", "special_delivery_1pm"),
                "country" to "GB"
            )),
            Triple("dpd_uk", "DPD UK", mutableMapOf<String, Any>(
                "tracking_url" to "https://track.dpd.co.uk/parcels/",
                "services" to listOf("next_day", "express", "saturday_delivery"),
                "country" to "GB"
            )),
            Triple("dhl_express", "DHL Express", mutableMapOf<String, Any>(
                "tracking_url" to "https://www.dhl.com/en/express/tracking.html?AWB=",
                "services" to listOf("express_worldwide", "express_9", "express_12", "economy_select"),
                "international" to true
            )),
            Triple("ups", "UPS", mutableMapOf<String, Any>(
                "tracking_url" to "https://www.ups.com/track?tracknum=",
                "services" to listOf("express_saver", "express", "express_plus", "standard"),
                "international" to true
            ))
        )

        providers.forEach { (id, providerName, config) ->
            fulfillmentProviderRepository.findById(id).orElseGet {
                FulfillmentProvider().apply {
                    this.id = id
                    this.name = providerName
                    this.providerId = id
                    this.isActive = true
                    this.config = config
                }.also {
                    fulfillmentProviderRepository.save(it)
                    logger.info { "Created fulfillment provider: $providerName" }
                }
            }
        }

        // Use Royal Mail as default provider for UK shipping
        val provider = fulfillmentProviderRepository.findById("royal_mail").get()

        // Shipping Options
        val existingOptions = shippingOptionRepository.findAll()
        val dpdProvider = fulfillmentProviderRepository.findById("dpd_uk").get()
        val dhlProvider = fulfillmentProviderRepository.findById("dhl_express").get()

        if (existingOptions.none { it.name == "Royal Mail Tracked 48" }) {
            ShippingOption().apply {
                name = "Royal Mail Tracked 48"
                regionId = region.id
                this.profile = profile
                this.provider = provider  // Royal Mail
                priceType = ShippingPriceType.FLAT_RATE
                amount = BigDecimal("4.95")
                isReturn = false
                data = mutableMapOf("id" to "rm-tracked48", "service" to "tracked48", "delivery_days" to "2-3")
            }.also {
                shippingOptionRepository.save(it)
                logger.info { "Created Royal Mail Tracked 48 option" }
            }
        }

        if (existingOptions.none { it.name == "Royal Mail Tracked 24" }) {
            ShippingOption().apply {
                name = "Royal Mail Tracked 24"
                regionId = region.id
                this.profile = profile
                this.provider = provider  // Royal Mail
                priceType = ShippingPriceType.FLAT_RATE
                amount = BigDecimal("6.95")
                isReturn = false
                data = mutableMapOf("id" to "rm-tracked24", "service" to "tracked24", "delivery_days" to "1-2")
            }.also {
                shippingOptionRepository.save(it)
                logger.info { "Created Royal Mail Tracked 24 option" }
            }
        }

        if (existingOptions.none { it.name == "DPD Next Day" }) {
            ShippingOption().apply {
                name = "DPD Next Day"
                regionId = region.id
                this.profile = profile
                this.provider = dpdProvider
                priceType = ShippingPriceType.FLAT_RATE
                amount = BigDecimal("8.95")
                isReturn = false
                data = mutableMapOf("id" to "dpd-next-day", "service" to "next_day", "delivery_days" to "1")
            }.also {
                shippingOptionRepository.save(it)
                logger.info { "Created DPD Next Day option" }
            }
        }

        if (existingOptions.none { it.name == "Royal Mail Special Delivery 9am" }) {
            ShippingOption().apply {
                name = "Royal Mail Special Delivery 9am"
                regionId = region.id
                this.profile = profile
                this.provider = provider  // Royal Mail
                priceType = ShippingPriceType.FLAT_RATE
                amount = BigDecimal("12.95")
                isReturn = false
                data = mutableMapOf("id" to "rm-special-9am", "service" to "special_delivery_9am", "delivery_days" to "1", "guaranteed" to true)
            }.also {
                shippingOptionRepository.save(it)
                logger.info { "Created Royal Mail Special Delivery 9am option" }
            }
        }

        if (existingOptions.none { it.name == "DHL Express International" }) {
            ShippingOption().apply {
                name = "DHL Express International"
                regionId = region.id
                this.profile = profile
                this.provider = dhlProvider
                priceType = ShippingPriceType.FLAT_RATE
                amount = BigDecimal("19.95")
                isReturn = false
                data = mutableMapOf("id" to "dhl-express-intl", "service" to "express_worldwide", "delivery_days" to "3-5", "international" to true)
            }.also {
                shippingOptionRepository.save(it)
                logger.info { "Created DHL Express International option" }
            }
        }

        return profile
    }

    @Transactional
    protected fun seedProductType(): ProductType {
        val existingType = productTypeRepository.findByValue("Fragrance")
        if (existingType != null) {
            logger.info { "ProductType 'Fragrance' already exists, skipping" }
            return existingType
        }

        return ProductType().apply {
            this.value = "Fragrance"
        }.also {
            productTypeRepository.save(it)
            logger.info { "Created ProductType: Fragrance" }
        }
    }

    @Transactional
    protected fun seedBrand(): Brand {
        val existingBrand = brandRepository.findBySlugIgnoreCase("vernont")
        if (existingBrand != null) {
            logger.info { "Brand 'Vernont' already exists, skipping" }
            return existingBrand
        }

        return Brand().apply {
            this.name = "Vernont"
            this.slug = "vernont"
            this.description = "Vernont - Luxury Fragrances. A curated collection of premium perfumes and colognes."
            this.active = true
            this.tier = BrandTier.PREMIUM
        }.also {
            brandRepository.save(it)
            logger.info { "Created Brand: Vernont" }
        }
    }

    @Transactional
    protected fun seedFragranceCategories(): Map<String, ProductCategory> {
        val categories = listOf(
            "Eau de Parfum",
            "Eau de Toilette",
            "Parfum",
            "Parfum Intense",
            "Eau de Cologne",
            "Discovery Sets"
        )
        var createdCount = 0
        val result = categories.associateWith { name ->
            val handle = name.lowercase().replace(" ", "-")
            productCategoryRepository.findByHandle(handle) ?: ProductCategory().apply {
                this.name = name
                this.handle = handle
                this.isActive = true
            }.also {
                productCategoryRepository.save(it)
                createdCount++
            }
        }
        if (createdCount > 0) {
            logger.info { "Created $createdCount fragrance categories" }
        } else {
            logger.info { "All fragrance categories already exist, skipping" }
        }
        return result
    }

    @Transactional
    protected fun seedCollections(): Map<String, ProductCollection> {
        val collectionData = listOf(
            Triple("Signature", "signature", "Our most iconic and luxurious fragrances"),
            Triple("Heritage", "heritage", "Timeless scents inspired by classic perfumery"),
            Triple("Floral", "floral", "Elegant bouquets of the finest florals"),
            Triple("Fresh", "fresh", "Light, invigorating scents for everyday wear"),
            Triple("Gourmand", "gourmand", "Sweet, edible-inspired fragrances")
        )
        var createdCount = 0
        val result = collectionData.associate { (title, handle, desc) ->
            title to (productCollectionRepository.findByHandle(handle) ?: ProductCollection().apply {
                this.title = title
                this.handle = handle
                this.description = desc
                this.published = true
            }.also {
                productCollectionRepository.save(it)
                createdCount++
            })
        }
        if (createdCount > 0) {
            logger.info { "Created $createdCount product collections" }
        } else {
            logger.info { "All product collections already exist, skipping" }
        }
        return result
    }

    @Transactional
    protected fun seedTags(): Map<String, ProductTag> {
        val tagValues = listOf(
            "For Her",
            "For Him",
            "Unisex",
            "New Arrival",
            "Bestseller"
        )
        var createdCount = 0
        val result = tagValues.associateWith { value ->
            productTagRepository.findByValue(value) ?: ProductTag().apply {
                this.value = value
            }.also {
                productTagRepository.save(it)
                createdCount++
            }
        }
        if (createdCount > 0) {
            logger.info { "Created $createdCount product tags" }
        } else {
            logger.info { "All product tags already exist, skipping" }
        }
        return result
    }

    @Transactional
    protected fun seedFragrances(
        categories: Map<String, ProductCategory>,
        collections: Map<String, ProductCollection>,
        tags: Map<String, ProductTag>,
        productType: ProductType,
        brand: Brand,
        shippingProfile: ShippingProfile,
        region: Region,
        stockLocation: StockLocation
    ) {
        // Midnight Rose - Women's Eau de Parfum
        createFragrance(
            title = "Midnight Rose",
            handle = "midnight-rose",
            description = "A captivating floral fragrance with deep rose and mysterious oud undertones. Top notes of Bulgarian Rose and Pink Pepper give way to a heart of Turkish Rose Absolute and Saffron, resting on a base of Oud, Musk, and Amber.",
            category = categories["Eau de Parfum"],
            collection = collections["Signature"],
            genderTag = tags["For Her"],
            additionalTags = listOf(tags["New Arrival"]),
            productType = productType,
            brand = brand,
            profile = shippingProfile,
            pricesGbp = mapOf("30ml" to BigDecimal("145"), "50ml" to BigDecimal("195"), "100ml" to BigDecimal("245")),
            stockLocation = stockLocation,
            imageUrl = "https://images.unsplash.com/photo-1541643600914-78b084683601?w=800&q=80"
        )

        // Golden Amber - Unisex Parfum
        createFragrance(
            title = "Golden Amber",
            handle = "golden-amber",
            description = "A warm, enveloping scent that wraps you in luxury. Bergamot and Cardamom open to reveal a rich heart of Amber and Labdanum, settling into Vanilla, Sandalwood, and Benzoin.",
            category = categories["Parfum"],
            collection = collections["Heritage"],
            genderTag = tags["Unisex"],
            additionalTags = listOf(tags["Bestseller"]),
            productType = productType,
            brand = brand,
            profile = shippingProfile,
            pricesGbp = mapOf("30ml" to BigDecimal("195"), "50ml" to BigDecimal("265"), "100ml" to BigDecimal("320")),
            stockLocation = stockLocation,
            imageUrl = "https://images.unsplash.com/photo-1594035910387-fea47794261f?w=800&q=80"
        )

        // Velvet Oud - Unisex Eau de Parfum
        createFragrance(
            title = "Velvet Oud",
            handle = "velvet-oud",
            description = "An opulent oriental fragrance with the finest oud. Saffron and Rose introduce a heart of precious Oud and Incense, grounded by Sandalwood and Musk.",
            category = categories["Eau de Parfum"],
            collection = collections["Signature"],
            genderTag = tags["Unisex"],
            additionalTags = emptyList(),
            productType = productType,
            brand = brand,
            profile = shippingProfile,
            pricesGbp = mapOf("30ml" to BigDecimal("245"), "50ml" to BigDecimal("325"), "100ml" to BigDecimal("395")),
            stockLocation = stockLocation,
            imageUrl = "https://images.unsplash.com/photo-1592945403244-b3fbafd7f539?w=800&q=80"
        )

        // Jasmine Dreams - Women's Eau de Toilette
        createFragrance(
            title = "Jasmine Dreams",
            handle = "jasmine-dreams",
            description = "A delicate floral journey through jasmine gardens at dusk. Orange Blossom and Neroli lead to Jasmine Sambac and Tuberose, finishing with White Musk and Cedar.",
            category = categories["Eau de Toilette"],
            collection = collections["Floral"],
            genderTag = tags["For Her"],
            additionalTags = emptyList(),
            productType = productType,
            brand = brand,
            profile = shippingProfile,
            pricesGbp = mapOf("30ml" to BigDecimal("165"), "50ml" to BigDecimal("225"), "100ml" to BigDecimal("275")),
            stockLocation = stockLocation,
            imageUrl = "https://images.unsplash.com/photo-1588405748880-12d1d2a59f75?w=800&q=80"
        )

        // Crystal Iris - Women's Eau de Parfum
        createFragrance(
            title = "Crystal Iris",
            handle = "crystal-iris",
            description = "A sophisticated powdery floral with iris at its heart. Bergamot and Pink Pepper open to Iris and Violet, settling into Orris Butter and Cashmere Wood.",
            category = categories["Eau de Parfum"],
            collection = collections["Floral"],
            genderTag = tags["For Her"],
            additionalTags = listOf(tags["New Arrival"]),
            productType = productType,
            brand = brand,
            profile = shippingProfile,
            pricesGbp = mapOf("30ml" to BigDecimal("175"), "50ml" to BigDecimal("235"), "100ml" to BigDecimal("285")),
            stockLocation = stockLocation,
            imageUrl = "https://images.unsplash.com/photo-1619994403073-2cec844b8e63?w=800&q=80"
        )

        // Noir Essence - Men's Parfum
        createFragrance(
            title = "Noir Essence",
            handle = "noir-essence",
            description = "A bold masculine scent with dark leather and spices. Black Pepper and Grapefruit give way to Leather and Tobacco, resting on Vetiver, Patchouli, and Amber.",
            category = categories["Parfum"],
            collection = collections["Signature"],
            genderTag = tags["For Him"],
            additionalTags = listOf(tags["New Arrival"]),
            productType = productType,
            brand = brand,
            profile = shippingProfile,
            pricesGbp = mapOf("30ml" to BigDecimal("215"), "50ml" to BigDecimal("285"), "100ml" to BigDecimal("355")),
            stockLocation = stockLocation,
            imageUrl = "https://images.unsplash.com/photo-1595425970377-c9703cf48b6d?w=800&q=80"
        )

        // Silk Magnolia - Women's Eau de Parfum
        createFragrance(
            title = "Silk Magnolia",
            handle = "silk-magnolia",
            description = "A luminous floral bouquet with dewy magnolia petals. Lemon and Pear open to Magnolia and Peony, softening into White Tea and Musk.",
            category = categories["Eau de Parfum"],
            collection = collections["Floral"],
            genderTag = tags["For Her"],
            additionalTags = listOf(tags["New Arrival"]),
            productType = productType,
            brand = brand,
            profile = shippingProfile,
            pricesGbp = mapOf("30ml" to BigDecimal("155"), "50ml" to BigDecimal("215"), "100ml" to BigDecimal("265")),
            stockLocation = stockLocation,
            imageUrl = "https://images.unsplash.com/photo-1587017539504-67cfbddac569?w=800&q=80"
        )

        // Royal Sandalwood - Men's Parfum Intense
        createFragrance(
            title = "Royal Sandalwood",
            handle = "royal-sandalwood",
            description = "A regal woody fragrance with the finest Indian sandalwood. Cardamom and Nutmeg lead to precious Sandalwood and Rose, grounded by Musk and Tonka Bean.",
            category = categories["Parfum Intense"],
            collection = collections["Heritage"],
            genderTag = tags["For Him"],
            additionalTags = listOf(tags["New Arrival"]),
            productType = productType,
            brand = brand,
            profile = shippingProfile,
            pricesGbp = mapOf("30ml" to BigDecimal("265"), "50ml" to BigDecimal("345"), "100ml" to BigDecimal("425")),
            stockLocation = stockLocation,
            imageUrl = "https://images.unsplash.com/photo-1590736969955-71cc94901144?w=800&q=80"
        )

        // Ocean Breeze - Men's Eau de Toilette
        createFragrance(
            title = "Ocean Breeze",
            handle = "ocean-breeze",
            description = "A refreshing aquatic scent inspired by Mediterranean coasts. Sea Salt and Citrus open to Marine Notes and Lavender, settling into Driftwood and Musk.",
            category = categories["Eau de Toilette"],
            collection = collections["Fresh"],
            genderTag = tags["For Him"],
            additionalTags = emptyList(),
            productType = productType,
            brand = brand,
            profile = shippingProfile,
            pricesGbp = mapOf("30ml" to BigDecimal("115"), "50ml" to BigDecimal("155"), "100ml" to BigDecimal("195")),
            stockLocation = stockLocation,
            imageUrl = "https://images.unsplash.com/photo-1523293182086-7651a899d37f?w=800&q=80"
        )

        // Vanilla Orchid - Women's Eau de Parfum
        createFragrance(
            title = "Vanilla Orchid",
            handle = "vanilla-orchid",
            description = "A sensual blend of exotic orchid and warm vanilla. Mandarin and Pear introduce Black Orchid and Jasmine, deepening into Vanilla, Patchouli, and Sandalwood.",
            category = categories["Eau de Parfum"],
            collection = collections["Gourmand"],
            genderTag = tags["For Her"],
            additionalTags = listOf(tags["Bestseller"]),
            productType = productType,
            brand = brand,
            profile = shippingProfile,
            pricesGbp = mapOf("30ml" to BigDecimal("175"), "50ml" to BigDecimal("235"), "100ml" to BigDecimal("285")),
            stockLocation = stockLocation,
            imageUrl = "https://images.unsplash.com/photo-1585386959984-a4155224a1ad?w=800&q=80"
        )

        // Bergamot Bliss - Unisex Eau de Cologne
        createFragrance(
            title = "Bergamot Bliss",
            handle = "bergamot-bliss",
            description = "A sparkling citrus fragrance with Italian bergamot. Bergamot and Lemon lead to Neroli and Petitgrain, settling into White Musk and Cedar.",
            category = categories["Eau de Cologne"],
            collection = collections["Fresh"],
            genderTag = tags["Unisex"],
            additionalTags = emptyList(),
            productType = productType,
            brand = brand,
            profile = shippingProfile,
            pricesGbp = mapOf("50ml" to BigDecimal("145"), "100ml" to BigDecimal("185"), "200ml" to BigDecimal("215")),
            stockLocation = stockLocation,
            imageUrl = "https://images.unsplash.com/photo-1595535873420-a599195b3f4a?w=800&q=80"
        )

        // Spiced Woods - Men's Eau de Parfum
        createFragrance(
            title = "Spiced Woods",
            handle = "spiced-woods",
            description = "A sophisticated woody spicy composition for the modern gentleman. Cinnamon and Ginger open to Cedar and Vetiver, grounded by Oud, Amber, and Musk.",
            category = categories["Eau de Parfum"],
            collection = collections["Heritage"],
            genderTag = tags["For Him"],
            additionalTags = listOf(tags["Bestseller"]),
            productType = productType,
            brand = brand,
            profile = shippingProfile,
            pricesGbp = mapOf("30ml" to BigDecimal("185"), "50ml" to BigDecimal("255"), "100ml" to BigDecimal("310")),
            stockLocation = stockLocation,
            imageUrl = "https://images.unsplash.com/photo-1547887538-e3a2f32cb1cc?w=800&q=80"
        )
    }

    private fun createFragrance(
        title: String,
        handle: String,
        description: String,
        category: ProductCategory?,
        collection: ProductCollection?,
        genderTag: ProductTag?,
        additionalTags: List<ProductTag?>,
        productType: ProductType,
        brand: Brand,
        profile: ShippingProfile,
        pricesGbp: Map<String, BigDecimal>,
        stockLocation: StockLocation,
        imageUrl: String
    ) {
        if (productRepository.findByHandle(handle) != null) {
            logger.info { "Fragrance $title already exists, skipping" }
            return
        }

        val product = Product().apply {
            this.title = title
            this.handle = handle
            this.description = description
            this.status = ProductStatus.PUBLISHED
            this.shippingProfileId = profile.id
            this.weight = "150" // Typical perfume bottle weight in grams
            this.thumbnail = imageUrl

            // Use proper entity relationships instead of metadata
            this.type = productType
            this.brand = brand
            this.collection = collection
        }

        // Add category
        category?.let { product.categories.add(it) }

        // Add tags (gender + additional)
        genderTag?.let { product.tags.add(it) }
        additionalTags.filterNotNull().forEach { product.tags.add(it) }

        // Create Size Option
        val sizeOption = ProductOption().apply {
            this.title = "Size"
            this.product = product
            this.position = 0
        }
        product.addOption(sizeOption)

        // Create Variants for each size
        pricesGbp.entries.forEachIndexed { index, (size, priceGbp) ->
            val variant = ProductVariant().apply {
                this.title = size
                this.sku = "${handle.uppercase().replace("-", "_")}_${size.replace("ml", "ML")}"
                this.product = product
                this.barcode = generateEAN13(handle, size)
            }

            // Add Size Option Value
            val variantOption = ProductVariantOption().apply {
                this.value = size
                this.option = sizeOption
                this.variant = variant
            }
            variant.addOption(variantOption)

            // Add Prices in multiple currencies
            val gbpCurrency = currencyRepository.findByCode("gbp")
                ?: throw IllegalStateException("GBP currency not found during product seeding.")
            val eurCurrency = currencyRepository.findByCode("eur")
                ?: throw IllegalStateException("EUR currency not found during product seeding.")
            val usdCurrency = currencyRepository.findByCode("usd")
                ?: throw IllegalStateException("USD currency not found during product seeding.")

            // GBP Price (base)
            val priceGbpEntity = ProductVariantPrice().apply {
                this.amount = priceGbp
                this.currencyCode = gbpCurrency.code
                this.variant = variant
            }

            // EUR Price (approx 1.15x GBP)
            val priceEurEntity = ProductVariantPrice().apply {
                this.amount = priceGbp.multiply(BigDecimal("1.15")).setScale(0, java.math.RoundingMode.HALF_UP)
                this.currencyCode = eurCurrency.code
                this.variant = variant
            }

            // USD Price (approx 1.25x GBP)
            val priceUsdEntity = ProductVariantPrice().apply {
                this.amount = priceGbp.multiply(BigDecimal("1.25")).setScale(0, java.math.RoundingMode.HALF_UP)
                this.currencyCode = usdCurrency.code
                this.variant = variant
            }

            variant.addPrice(priceGbpEntity)
            variant.addPrice(priceEurEntity)
            variant.addPrice(priceUsdEntity)

            product.variants.add(variant)

            // Inventory
            val inventoryItem = InventoryItem().apply {
                this.sku = variant.sku
                this.originCountry = "GB"
                this.hsCode = "330300" // HS Code for perfumes
            }.also { inventoryItemRepository.save(it) }

            InventoryLevel().apply {
                this.inventoryItem = inventoryItem
                this.location = stockLocation
                this.stockedQuantity = 50
                this.incomingQuantity = 0
                this.recalculateAvailableQuantity()
            }.also { inventoryLevelRepository.save(it) }
        }

        productRepository.save(product)
        logger.info { "Created fragrance: $title with ${pricesGbp.size} variants" }
    }

    private fun generateEAN13(handle: String, size: String): String {
        // Generate a pseudo-EAN13 barcode based on product and size
        val baseCode = handle.hashCode().toString().takeLast(6).padStart(6, '0')
        val sizeCode = size.replace("ml", "").padStart(3, '0')
        val prefix = "590" // Custom prefix for Vernont
        val partialCode = "$prefix$baseCode$sizeCode"

        // Calculate check digit
        var sum = 0
        partialCode.forEachIndexed { index, char ->
            val digit = char.digitToInt()
            sum += if (index % 2 == 0) digit else digit * 3
        }
        val checkDigit = (10 - (sum % 10)) % 10

        return "$partialCode$checkDigit"
    }
}
