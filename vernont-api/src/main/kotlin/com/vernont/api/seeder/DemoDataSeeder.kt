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
    private val currencyRepository: com.vernont.repository.region.CurrencyRepository
) : CommandLineRunner {

    override fun run(vararg args: String) = runBlocking {
        val seedingEnabled = System.getenv("DEMO_SEED")?.equals("true", ignoreCase = true) == true
        if (!seedingEnabled) {
            logger.info { "Demo data seeding disabled (set DEMO_SEED=true to enable)." }
            return@runBlocking
        }

        // Check if demo products already exist
        val demoProductHandles = listOf("t-shirt", "sweatshirt", "sweatpants", "shorts")
        val existingDemoProducts = demoProductHandles.count { handle ->
            productRepository.findByHandle(handle) != null
        }
        
        if (existingDemoProducts == demoProductHandles.size) {
            logger.info { "Demo data already seeded (all demo products exist). Skipping." }
            return@runBlocking
        }

        logger.info { "Starting demo data seeding..." }

        try {
            seedCurrencies()
            seedStoreAndSalesChannel()
            val region = seedRegion()
            val stockLocation = seedStockLocation()
            val shippingProfile = seedFulfillment(region, stockLocation)
            val categories = seedProductCategories()
            seedProducts(categories, shippingProfile, region, stockLocation)

            logger.info { "Demo data seeding completed successfully." }
        } catch (e: Exception) {
            logger.error(e) { "Failed to seed demo data" }
        }
    }

    @Transactional
    protected fun seedCurrencies() {
        val currencies = listOf(
            Triple("eur", "€", "Euro"),
            Triple("usd", "$", "US Dollar"),
            Triple("gbp", "£", "British Pound")
        )
        
        currencies.forEach { (code, symbol, name) ->
            if (currencyRepository.findByCode(code) == null) {
                com.vernont.domain.region.Currency().apply {
                    this.code = code
                    this.symbol = symbol
                    this.symbolNative = symbol
                    this.name = name
                    this.decimalDigits = 2
                    this.rounding = 0.0
                    this.includesTax = false
                }.also {
                    currencyRepository.save(it)
                    logger.info { "Created currency: $code" }
                }
            }
        }
    }

    @Transactional
    protected fun seedStoreAndSalesChannel() {
        // Ensure default store exists
        val store = storeRepository.findAll().firstOrNull() ?: Store().apply {
            name = "Medusa Store"
            defaultCurrencyCode = "eur"
            swapLinkTemplate = "https://medusa-test.com/swap={swap_id}"
            paymentLinkTemplate = "https://medusa-test.com/payment={payment_id}"
            inviteLinkTemplate = "https://medusa-test.com/invite={invite_id}"
        }.also { 
            storeRepository.save(it)
            logger.info { "Created default store" }
        }

        // Ensure default sales channel exists
        val salesChannel = salesChannelRepository.findByName("Default Sales Channel") ?: SalesChannel().apply {
            name = "Default Sales Channel"
            description = "Created by Medusa"
        }.also { salesChannelRepository.save(it) }
        
        // Update store currencies
        val existingCurrencies = storeCurrencyRepository.findByStoreId(store.id)
        
        if (existingCurrencies.none { it.currencyCode == "eur" }) {
            StoreCurrency().apply {
                currencyCode = "eur"
                isDefault = true
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
        val existingRegion = regionRepository.findByName("Europe")
        if (existingRegion != null) {
            logger.info { "Europe region already exists, skipping" }
            return existingRegion
        }
        
        val countries = countryRepository.findAllById(listOf("GB", "DE", "DK", "SE", "FR", "ES", "IT"))
        
        return Region().apply {
            name = "Europe"
            currencyCode = "eur"
            taxRate = BigDecimal("0.25") // 25% as decimal
            this.countries.addAll(countries)
        }.also { 
            regionRepository.save(it)
            logger.info { "Created Europe region with ${countries.size} countries" }
        }
    }

    @Transactional
    protected fun seedStockLocation(): StockLocation {
        val existingLocation = stockLocationRepository.findByName("European Warehouse")
        if (existingLocation != null) {
            logger.info { "European Warehouse already exists, skipping" }
            return existingLocation
        }
        
        return StockLocation().apply {
            name = "European Warehouse"
            address1 = ""
            city = "Copenhagen"
            countryCode = "DK"
        }.also { 
            stockLocationRepository.save(it)
            logger.info { "Created European Warehouse stock location" }
        }
    }

    @Transactional
    protected fun seedFulfillment(region: Region, stockLocation: StockLocation): ShippingProfile {
        // Shipping Profile
        val profile = shippingProfileRepository.findByName("Default Shipping Profile") ?: ShippingProfile().apply {
            name = "Default Shipping Profile"
            type = ShippingProfileType.DEFAULT
        }.also { 
            shippingProfileRepository.save(it)
            logger.info { "Created Default Shipping Profile" }
        }

        // Fulfillment Provider (Manual)
        val provider = fulfillmentProviderRepository.findById("manual").orElseGet {
            FulfillmentProvider().apply {
                id = "manual"
                name = "Manual"
                providerId = "manual" // Set the providerId here
                isActive = true
                config = mutableMapOf() // Initialize config as an empty mutable map
            }.also { 
                fulfillmentProviderRepository.save(it)
                logger.info { "Created manual fulfillment provider" }
            }
        }

        // Shipping Options
        val existingOptions = shippingOptionRepository.findAll()
        
        if (existingOptions.none { it.name == "Standard Shipping" }) {
            ShippingOption().apply {
                name = "Standard Shipping"
                regionId = region.id
                this.profile = profile
                this.provider = provider
                priceType = ShippingPriceType.FLAT_RATE
                amount = BigDecimal("10.00")
                isReturn = false
                data = mutableMapOf("id" to "standard-shipping")
            }.also { 
                shippingOptionRepository.save(it)
                logger.info { "Created Standard Shipping option" }
            }
        }

        if (existingOptions.none { it.name == "Express Shipping" }) {
            ShippingOption().apply {
                name = "Express Shipping"
                regionId = region.id
                this.profile = profile
                this.provider = provider
                priceType = ShippingPriceType.FLAT_RATE
                amount = BigDecimal("15.00")
                isReturn = false
                data = mutableMapOf("id" to "express-shipping")
            }.also { 
                shippingOptionRepository.save(it)
                logger.info { "Created Express Shipping option" }
            }
        }
        
        return profile
    }

    @Transactional
    protected fun seedProductCategories(): Map<String, ProductCategory> {
        val categories = listOf("Shirts", "Sweatshirts", "Pants", "Merch")
        var createdCount = 0
        val result = categories.associateWith { name ->
            val handle = name.lowercase()
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
            logger.info { "Created $createdCount product categories" }
        } else {
            logger.info { "All product categories already exist, skipping" }
        }
        return result
    }

    @Transactional
    protected fun seedProducts(
        categories: Map<String, ProductCategory>,
        shippingProfile: ShippingProfile,
        region: Region,
        stockLocation: StockLocation
    ) {
        // T-Shirt
        createProduct(
            title = "Medusa T-Shirt",
            handle = "t-shirt",
            description = "Reimagine the feeling of a classic T-shirt.",
            category = categories["Shirts"],
            profile = shippingProfile,
            options = listOf("Size", "Color"),
            variants = listOf(
                VariantData("S / Black", "SHIRT-S-BLACK", mapOf("Size" to "S", "Color" to "Black")),
                VariantData("S / White", "SHIRT-S-WHITE", mapOf("Size" to "S", "Color" to "White")),
                VariantData("M / Black", "SHIRT-M-BLACK", mapOf("Size" to "M", "Color" to "Black")),
                VariantData("M / White", "SHIRT-M-WHITE", mapOf("Size" to "M", "Color" to "White")),
                VariantData("L / Black", "SHIRT-L-BLACK", mapOf("Size" to "L", "Color" to "Black")),
                VariantData("L / White", "SHIRT-L-WHITE", mapOf("Size" to "L", "Color" to "White")),
                VariantData("XL / Black", "SHIRT-XL-BLACK", mapOf("Size" to "XL", "Color" to "Black")),
                VariantData("XL / White", "SHIRT-XL-WHITE", mapOf("Size" to "XL", "Color" to "White"))
            ),
            stockLocation = stockLocation
        )

        // Sweatshirt
        createProduct(
            title = "Medusa Sweatshirt",
            handle = "sweatshirt",
            description = "Reimagine the feeling of a classic sweatshirt.",
            category = categories["Sweatshirts"],
            profile = shippingProfile,
            options = listOf("Size"),
            variants = listOf(
                VariantData("S", "SWEATSHIRT-S", mapOf("Size" to "S")),
                VariantData("M", "SWEATSHIRT-M", mapOf("Size" to "M")),
                VariantData("L", "SWEATSHIRT-L", mapOf("Size" to "L")),
                VariantData("XL", "SWEATSHIRT-XL", mapOf("Size" to "XL"))
            ),
            stockLocation = stockLocation
        )
        
        // Sweatpants
        createProduct(
            title = "Medusa Sweatpants",
            handle = "sweatpants",
            description = "Reimagine the feeling of classic sweatpants.",
            category = categories["Pants"],
            profile = shippingProfile,
            options = listOf("Size"),
            variants = listOf(
                VariantData("S", "SWEATPANTS-S", mapOf("Size" to "S")),
                VariantData("M", "SWEATPANTS-M", mapOf("Size" to "M")),
                VariantData("L", "SWEATPANTS-L", mapOf("Size" to "L")),
                VariantData("XL", "SWEATPANTS-XL", mapOf("Size" to "XL"))
            ),
            stockLocation = stockLocation
        )
        
        // Shorts
        createProduct(
            title = "Medusa Shorts",
            handle = "shorts",
            description = "Reimagine the feeling of classic shorts.",
            category = categories["Merch"],
            profile = shippingProfile,
            options = listOf("Size"),
            variants = listOf(
                VariantData("S", "SHORTS-S", mapOf("Size" to "S")),
                VariantData("M", "SHORTS-M", mapOf("Size" to "M")),
                VariantData("L", "SHORTS-L", mapOf("Size" to "L")),
                VariantData("XL", "SHORTS-XL", mapOf("Size" to "XL"))
            ),
            stockLocation = stockLocation
        )
    }

    private fun createProduct(
        title: String,
        handle: String,
        description: String,
        category: ProductCategory?,
        profile: ShippingProfile,
        options: List<String>,
        variants: List<VariantData>,
        stockLocation: StockLocation
    ) {
        if (productRepository.findByHandle(handle) != null) return

        val product = Product().apply {
            this.title = title
            this.handle = handle
            this.description = description
            this.status = ProductStatus.PUBLISHED
            this.shippingProfileId = profile.id
            this.weight = "400"
        }
        
        category?.let { product.categories.add(it) }
        
        // Options and variants will be persisted by cascading from the product save

        // Create Options
        options.forEachIndexed { index, optionTitle ->
            val option = ProductOption().apply {
                this.title = optionTitle
                this.product = product
                this.position = index
            }
            product.addOption(option)
        }
        
        // Create Variants
        variants.forEach { variantData ->
            val variant = ProductVariant().apply {
                this.title = variantData.title
                this.sku = variantData.sku
                this.product = product
            }
            
            // Add Options
            variantData.options.forEach { (optTitle, optValue) ->
                val productOption = product.options.find { it.title == optTitle }
                if (productOption != null) {
                    val variantOption = ProductVariantOption().apply {
                        this.value = optValue
                        this.option = productOption
                        this.variant = variant
                    }
                    variant.addOption(variantOption)
                }
            }
            
            // Add Prices
            val eurCurrency = currencyRepository.findByCode("eur") ?: throw IllegalStateException("EUR currency not found during product seeding.")
            val usdCurrency = currencyRepository.findByCode("usd") ?: throw IllegalStateException("USD currency not found during product seeding.")

            val priceEur = ProductVariantPrice().apply {
                this.amount = BigDecimal("10.00")
                this.currencyCode = eurCurrency.code
                this.variant = variant
            }
            val priceUsd = ProductVariantPrice().apply {
                this.amount = BigDecimal("15.00")
                this.currencyCode = usdCurrency.code
                this.variant = variant
            }
            variant.addPrice(priceEur)
            variant.addPrice(priceUsd)

            product.variants.add(variant)
            
            // Inventory
            val inventoryItem = InventoryItem().apply {
                this.sku = variant.sku
                this.originCountry = "EU"
                this.hsCode = "123456"
            }.also { inventoryItemRepository.save(it) }
            
            InventoryLevel().apply {
                this.inventoryItem = inventoryItem
                this.location = stockLocation
                this.stockedQuantity = 100
                this.incomingQuantity = 0
                this.recalculateAvailableQuantity()
            }.also { inventoryLevelRepository.save(it) }
        }
        
        productRepository.save(product)
    }

    data class VariantData(
        val title: String,
        val sku: String,
        val options: Map<String, String>
    )
}
