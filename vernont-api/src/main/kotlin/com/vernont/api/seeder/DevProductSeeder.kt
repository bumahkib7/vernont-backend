package com.vernont.api.seeder

import com.vernont.domain.product.*
import com.vernont.domain.region.Region
import com.vernont.repository.product.*
import com.vernont.repository.region.CurrencyRepository
import com.vernont.repository.region.RegionRepository
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.runBlocking
import org.springframework.boot.CommandLineRunner
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal

private val logger = KotlinLogging.logger {}

@Component
@Order(100) // Run after CurrencySeeder (@Order(1)) and other seeders
class DevProductSeeder(
    private val brandRepository: BrandRepository,
    private val productCollectionRepository: ProductCollectionRepository,
    private val productCategoryRepository: ProductCategoryRepository,
    private val canonicalCategoryRepository: CanonicalCategoryRepository,
    private val productRepository: ProductRepository,
    private val currencyRepository: CurrencyRepository,
    private val regionRepository: RegionRepository,
    private val productImageRepository: ProductImageRepository
) : CommandLineRunner {

    override fun run(vararg args: String) = runBlocking {
        val seedingEnabled = System.getenv("DEV_SEED")?.equals("true", ignoreCase = true) == true
        if (!seedingEnabled) {
            logger.info { "Dev product seeding disabled (set DEV_SEED=true to enable)." }
            return@runBlocking
        }

        // Check if dev products already exist
        if (productRepository.findByHandle("dev-nike-air-max") != null) {
            logger.info { "Dev products already seeded. Skipping." }
            return@runBlocking
        }

        logger.info { "Starting dev product seeding..." }

        try {
            val brands = seedBrands()
            val collections = seedCollections()
            val categories = seedCategories()
            seedProducts(brands, collections, categories)
            logger.info { "Dev product seeding completed successfully." }
        } catch (e: Exception) {
            logger.error(e) { "Failed to seed dev products" }
        }
    }

    @Transactional
    fun seedBrands(): Map<String, Brand> {
        val brandsData = listOf(
            BrandData("Nike", "nike", BrandTier.STANDARD, "https://images.unsplash.com/photo-1542291026-7eec264c27ff?w=200"),
            BrandData("Adidas", "adidas", BrandTier.STANDARD, "https://images.unsplash.com/photo-1518002171953-a080ee817e1f?w=200"),
            BrandData("Gucci", "gucci", BrandTier.LUXURY, "https://images.unsplash.com/photo-1548036328-c9fa89d128fa?w=200"),
            BrandData("Prada", "prada", BrandTier.LUXURY, "https://images.unsplash.com/photo-1584917865442-de89df76afd3?w=200"),
            BrandData("Louis Vuitton", "louis-vuitton", BrandTier.LUXURY, "https://images.unsplash.com/photo-1591561954557-26941169b49e?w=200"),
            BrandData("Zara", "zara", BrandTier.STANDARD, "https://images.unsplash.com/photo-1558618666-fcd25c85cd64?w=200"),
            BrandData("H&M", "hm", BrandTier.STANDARD, null),
            BrandData("Balenciaga", "balenciaga", BrandTier.LUXURY, "https://images.unsplash.com/photo-1539185441755-769473a23570?w=200")
        )

        return brandsData.associate { data ->
            val brand = brandRepository.findBySlugIgnoreCase(data.slug) ?: Brand().apply {
                name = data.name
                slug = data.slug
                tier = data.tier
                logoUrl = data.logoUrl
                active = true
            }.also { brandRepository.save(it) }
            data.slug to brand
        }.also { logger.info { "Seeded ${it.size} brands" } }
    }

    @Transactional
    fun seedCollections(): Map<String, ProductCollection> {
        val collectionsData = listOf(
            CollectionData("New Arrivals", "new-arrivals", "Fresh styles just dropped", "https://images.unsplash.com/photo-1441986300917-64674bd600d8?w=800"),
            CollectionData("Menswear", "menswear", "Curated collection for men", "https://images.unsplash.com/photo-1490578474895-699cd4e2cf59?w=800"),
            CollectionData("Womenswear", "womenswear", "Elegant styles for women", "https://images.unsplash.com/photo-1483985988355-763728e1935b?w=800"),
            CollectionData("Footwear", "footwear", "Step up your shoe game", "https://images.unsplash.com/photo-1542291026-7eec264c27ff?w=800"),
            CollectionData("Accessories", "accessories", "Complete your look", "https://images.unsplash.com/photo-1523275335684-37898b6baf30?w=800"),
            CollectionData("Sale", "sale", "Best deals and discounts", "https://images.unsplash.com/photo-1607082348824-0a96f2a4b9da?w=800"),
            CollectionData("Best Sellers", "best-sellers", "Top picks loved by everyone", "https://images.unsplash.com/photo-1556905055-8f358a7a47b2?w=800"),
            CollectionData("Luxury Edit", "luxury-edit", "Premium designer pieces", "https://images.unsplash.com/photo-1549298916-b41d501d3772?w=800")
        )

        return collectionsData.associate { data ->
            val collection = productCollectionRepository.findByHandle(data.handle) ?: ProductCollection().apply {
                title = data.title
                handle = data.handle
                description = data.description
                imageUrl = data.thumbnail
                published = true
            }.also { productCollectionRepository.save(it) }
            data.handle to collection
        }.also { logger.info { "Seeded ${it.size} collections" } }
    }

    @Transactional
    fun seedCategories(): Map<String, ProductCategory> {
        val categoriesData = listOf(
            CategoryData("Shoes", "shoes", null),
            CategoryData("Sneakers", "sneakers", "shoes"),
            CategoryData("Boots", "boots", "shoes"),
            CategoryData("Sandals", "sandals", "shoes"),
            CategoryData("Clothing", "clothing", null),
            CategoryData("T-Shirts", "t-shirts", "clothing"),
            CategoryData("Jackets", "jackets", "clothing"),
            CategoryData("Dresses", "dresses", "clothing"),
            CategoryData("Pants", "pants", "clothing"),
            CategoryData("Bags", "bags", null),
            CategoryData("Handbags", "handbags", "bags"),
            CategoryData("Backpacks", "backpacks", "bags"),
            CategoryData("Accessories", "accessories-cat", null),
            CategoryData("Watches", "watches", "accessories-cat"),
            CategoryData("Sunglasses", "sunglasses", "accessories-cat"),
            CategoryData("Jewelry", "jewelry", "accessories-cat")
        )

        val result = mutableMapOf<String, ProductCategory>()

        // First pass: create all categories
        categoriesData.forEach { data ->
            val existing = productCategoryRepository.findByHandle(data.handle)
            if (existing != null) {
                result[data.handle] = existing
            } else {
                val category = ProductCategory().apply {
                    name = data.name
                    handle = data.handle
                    isActive = true
                }
                productCategoryRepository.save(category)
                result[data.handle] = category
            }
        }

        // Second pass: set parent relationships
        categoriesData.filter { it.parentHandle != null }.forEach { data ->
            val category = result[data.handle]!!
            val parent = result[data.parentHandle]
            if (parent != null && category.parent == null) {
                category.parent = parent
                productCategoryRepository.save(category)
            }
        }

        logger.info { "Seeded ${result.size} product categories" }
        return result
    }

    @Transactional
    fun seedProducts(
        brands: Map<String, Brand>,
        collections: Map<String, ProductCollection>,
        categories: Map<String, ProductCategory>
    ) {
        val gbp = currencyRepository.findByCode("GBP") ?: throw IllegalStateException("GBP currency not found")
        val eur = currencyRepository.findByCode("EUR") ?: throw IllegalStateException("EUR currency not found")
        val usd = currencyRepository.findByCode("USD") ?: throw IllegalStateException("USD currency not found")

        // Get regions for price association
        val ukRegion = regionRepository.findByNameAndDeletedAtIsNull("United Kingdom")
        val euRegion = regionRepository.findByNameAndDeletedAtIsNull("European Union")
        val usRegion = regionRepository.findByNameAndDeletedAtIsNull("United States")

        if (ukRegion == null || euRegion == null || usRegion == null) {
            logger.warn { "Some regions not found. Prices will be created without region association." }
        }

        val shoesCategory = canonicalCategoryRepository.findBySlugAndDeletedAtIsNull("aa-8")
        val clothingCategory = canonicalCategoryRepository.findBySlugAndDeletedAtIsNull("aa-1")
        val bagsCategory = canonicalCategoryRepository.findBySlugAndDeletedAtIsNull("aa-5")
        val jewelryCategory = canonicalCategoryRepository.findBySlugAndDeletedAtIsNull("aa-6")

        val products = listOf(
            // Nike products
            ProductData(
                title = "Nike Air Max 90",
                handle = "dev-nike-air-max",
                description = "The Nike Air Max 90 stays true to its OG running roots with the iconic Waffle sole, stitched overlays and classic TPU details. Fresh colors give a modern look while Max Air cushioning adds comfort to your journey.",
                brand = brands["nike"],
                collections = listOf(collections["footwear"], collections["new-arrivals"], collections["best-sellers"]),
                categories = listOf(categories["sneakers"]),
                canonicalCategory = shoesCategory,
                thumbnail = "https://images.unsplash.com/photo-1542291026-7eec264c27ff?w=600",
                images = listOf(
                    "https://images.unsplash.com/photo-1542291026-7eec264c27ff?w=800",
                    "https://images.unsplash.com/photo-1606107557195-0e29a4b5b4aa?w=800",
                    "https://images.unsplash.com/photo-1600185365926-3a2ce3cdb9eb?w=800"
                ),
                basePrice = BigDecimal("129.99"),
                variants = listOf(
                    VariantInfo("UK 7", "NAM90-UK7", mapOf("Size" to "UK 7")),
                    VariantInfo("UK 8", "NAM90-UK8", mapOf("Size" to "UK 8")),
                    VariantInfo("UK 9", "NAM90-UK9", mapOf("Size" to "UK 9")),
                    VariantInfo("UK 10", "NAM90-UK10", mapOf("Size" to "UK 10")),
                    VariantInfo("UK 11", "NAM90-UK11", mapOf("Size" to "UK 11"))
                )
            ),
            ProductData(
                title = "Nike Dunk Low Retro",
                handle = "dev-nike-dunk-low",
                description = "Created for the hardwood but taken to the streets, the '80s basketball icon returns with classic details and throwback hoops flair.",
                brand = brands["nike"],
                collections = listOf(collections["footwear"], collections["menswear"]),
                categories = listOf(categories["sneakers"]),
                canonicalCategory = shoesCategory,
                thumbnail = "https://images.unsplash.com/photo-1597045566677-8cf032ed6634?w=600",
                images = listOf(
                    "https://images.unsplash.com/photo-1597045566677-8cf032ed6634?w=800",
                    "https://images.unsplash.com/photo-1595950653106-6c9ebd614d3a?w=800"
                ),
                basePrice = BigDecimal("109.99"),
                variants = listOf(
                    VariantInfo("UK 6 / White", "NDL-UK6-W", mapOf("Size" to "UK 6", "Color" to "White")),
                    VariantInfo("UK 7 / White", "NDL-UK7-W", mapOf("Size" to "UK 7", "Color" to "White")),
                    VariantInfo("UK 8 / White", "NDL-UK8-W", mapOf("Size" to "UK 8", "Color" to "White")),
                    VariantInfo("UK 6 / Black", "NDL-UK6-B", mapOf("Size" to "UK 6", "Color" to "Black")),
                    VariantInfo("UK 7 / Black", "NDL-UK7-B", mapOf("Size" to "UK 7", "Color" to "Black")),
                    VariantInfo("UK 8 / Black", "NDL-UK8-B", mapOf("Size" to "UK 8", "Color" to "Black"))
                )
            ),

            // Adidas products
            ProductData(
                title = "Adidas Ultraboost 22",
                handle = "dev-adidas-ultraboost",
                description = "These running shoes serve up comfort and responsiveness. The adidas PRIMEKNIT upper hugs your foot while BOOST cushioning delivers endless energy.",
                brand = brands["adidas"],
                collections = listOf(collections["footwear"], collections["new-arrivals"]),
                categories = listOf(categories["sneakers"]),
                canonicalCategory = shoesCategory,
                thumbnail = "https://images.unsplash.com/photo-1587563871167-1ee9c731aefb?w=600",
                images = listOf(
                    "https://images.unsplash.com/photo-1587563871167-1ee9c731aefb?w=800",
                    "https://images.unsplash.com/photo-1608231387042-66d1773070a5?w=800"
                ),
                basePrice = BigDecimal("159.99"),
                variants = listOf(
                    VariantInfo("UK 7", "AUB22-UK7", mapOf("Size" to "UK 7")),
                    VariantInfo("UK 8", "AUB22-UK8", mapOf("Size" to "UK 8")),
                    VariantInfo("UK 9", "AUB22-UK9", mapOf("Size" to "UK 9")),
                    VariantInfo("UK 10", "AUB22-UK10", mapOf("Size" to "UK 10"))
                )
            ),
            ProductData(
                title = "Adidas Stan Smith",
                handle = "dev-adidas-stan-smith",
                description = "A timeless classic. The Stan Smith shoe has been an icon since its debut in the '70s. Simple, clean, and always in style.",
                brand = brands["adidas"],
                collections = listOf(collections["footwear"], collections["best-sellers"]),
                categories = listOf(categories["sneakers"]),
                canonicalCategory = shoesCategory,
                thumbnail = "https://images.unsplash.com/photo-1549298916-b41d501d3772?w=600",
                images = listOf(
                    "https://images.unsplash.com/photo-1549298916-b41d501d3772?w=800"
                ),
                basePrice = BigDecimal("89.99"),
                variants = listOf(
                    VariantInfo("UK 6", "ASS-UK6", mapOf("Size" to "UK 6")),
                    VariantInfo("UK 7", "ASS-UK7", mapOf("Size" to "UK 7")),
                    VariantInfo("UK 8", "ASS-UK8", mapOf("Size" to "UK 8")),
                    VariantInfo("UK 9", "ASS-UK9", mapOf("Size" to "UK 9"))
                )
            ),

            // Gucci products
            ProductData(
                title = "Gucci Ace Sneakers",
                handle = "dev-gucci-ace",
                description = "The Gucci Ace sneaker in white leather with the House Web stripe and an interlocking G detail. A signature silhouette that combines luxury and sport.",
                brand = brands["gucci"],
                collections = listOf(collections["footwear"], collections["luxury-edit"], collections["new-arrivals"]),
                categories = listOf(categories["sneakers"]),
                canonicalCategory = shoesCategory,
                thumbnail = "https://images.unsplash.com/photo-1560769629-975ec94e6a86?w=600",
                images = listOf(
                    "https://images.unsplash.com/photo-1560769629-975ec94e6a86?w=800",
                    "https://images.unsplash.com/photo-1603808033192-082d6919d3e1?w=800"
                ),
                basePrice = BigDecimal("650.00"),
                variants = listOf(
                    VariantInfo("EU 39", "GAC-EU39", mapOf("Size" to "EU 39")),
                    VariantInfo("EU 40", "GAC-EU40", mapOf("Size" to "EU 40")),
                    VariantInfo("EU 41", "GAC-EU41", mapOf("Size" to "EU 41")),
                    VariantInfo("EU 42", "GAC-EU42", mapOf("Size" to "EU 42")),
                    VariantInfo("EU 43", "GAC-EU43", mapOf("Size" to "EU 43"))
                )
            ),
            ProductData(
                title = "Gucci GG Marmont Bag",
                handle = "dev-gucci-marmont",
                description = "The GG Marmont small matelassé shoulder bag has a softly structured shape and an oversized flap closure with Double G hardware.",
                brand = brands["gucci"],
                collections = listOf(collections["accessories"], collections["luxury-edit"], collections["womenswear"]),
                categories = listOf(categories["handbags"]),
                canonicalCategory = bagsCategory,
                thumbnail = "https://images.unsplash.com/photo-1548036328-c9fa89d128fa?w=600",
                images = listOf(
                    "https://images.unsplash.com/photo-1548036328-c9fa89d128fa?w=800",
                    "https://images.unsplash.com/photo-1584917865442-de89df76afd3?w=800"
                ),
                basePrice = BigDecimal("1890.00"),
                variants = listOf(
                    VariantInfo("Black", "GGM-BLK", mapOf("Color" to "Black")),
                    VariantInfo("Dusty Pink", "GGM-PNK", mapOf("Color" to "Dusty Pink")),
                    VariantInfo("Red", "GGM-RED", mapOf("Color" to "Red"))
                )
            ),

            // Prada products
            ProductData(
                title = "Prada Re-Nylon Backpack",
                handle = "dev-prada-backpack",
                description = "The iconic Prada backpack is made of Re-Nylon, an innovative nylon fabric made from recycled plastic. Features the signature enameled metal triangle logo.",
                brand = brands["prada"],
                collections = listOf(collections["accessories"], collections["luxury-edit"]),
                categories = listOf(categories["backpacks"]),
                canonicalCategory = bagsCategory,
                thumbnail = "https://images.unsplash.com/photo-1553062407-98eeb64c6a62?w=600",
                images = listOf(
                    "https://images.unsplash.com/photo-1553062407-98eeb64c6a62?w=800",
                    "https://images.unsplash.com/photo-1581605405669-fcdf81165afa?w=800"
                ),
                basePrice = BigDecimal("1250.00"),
                variants = listOf(
                    VariantInfo("Black", "PRN-BLK", mapOf("Color" to "Black")),
                    VariantInfo("Navy", "PRN-NVY", mapOf("Color" to "Navy"))
                )
            ),

            // Louis Vuitton products
            ProductData(
                title = "Louis Vuitton Neverfull MM",
                handle = "dev-lv-neverfull",
                description = "The iconic Neverfull tote in Monogram canvas with natural cowhide leather trim. Features side laces that cinch for a sleek look.",
                brand = brands["louis-vuitton"],
                collections = listOf(collections["accessories"], collections["luxury-edit"], collections["womenswear"], collections["best-sellers"]),
                categories = listOf(categories["handbags"]),
                canonicalCategory = bagsCategory,
                thumbnail = "https://images.unsplash.com/photo-1591561954557-26941169b49e?w=600",
                images = listOf(
                    "https://images.unsplash.com/photo-1591561954557-26941169b49e?w=800",
                    "https://images.unsplash.com/photo-1594223274512-ad4803739b7c?w=800"
                ),
                basePrice = BigDecimal("1540.00"),
                variants = listOf(
                    VariantInfo("Monogram / Beige", "LVN-MON-BG", mapOf("Pattern" to "Monogram", "Interior" to "Beige")),
                    VariantInfo("Monogram / Cherry", "LVN-MON-CH", mapOf("Pattern" to "Monogram", "Interior" to "Cherry")),
                    VariantInfo("Damier Ebene / Red", "LVN-DAM-RD", mapOf("Pattern" to "Damier Ebene", "Interior" to "Red"))
                )
            ),

            // Zara products
            ProductData(
                title = "Zara Oversized Blazer",
                handle = "dev-zara-blazer",
                description = "Relaxed fit blazer with lapel collar and long sleeves. Front welt pockets and inner pocket. Front button closure.",
                brand = brands["zara"],
                collections = listOf(collections["womenswear"], collections["new-arrivals"]),
                categories = listOf(categories["jackets"]),
                canonicalCategory = clothingCategory,
                thumbnail = "https://images.unsplash.com/photo-1594938298603-c8148c4dae35?w=600",
                images = listOf(
                    "https://images.unsplash.com/photo-1594938298603-c8148c4dae35?w=800",
                    "https://images.unsplash.com/photo-1591369822096-ffd140ec948f?w=800"
                ),
                basePrice = BigDecimal("79.99"),
                variants = listOf(
                    VariantInfo("XS / Black", "ZOB-XS-B", mapOf("Size" to "XS", "Color" to "Black")),
                    VariantInfo("S / Black", "ZOB-S-B", mapOf("Size" to "S", "Color" to "Black")),
                    VariantInfo("M / Black", "ZOB-M-B", mapOf("Size" to "M", "Color" to "Black")),
                    VariantInfo("L / Black", "ZOB-L-B", mapOf("Size" to "L", "Color" to "Black")),
                    VariantInfo("XS / Cream", "ZOB-XS-C", mapOf("Size" to "XS", "Color" to "Cream")),
                    VariantInfo("S / Cream", "ZOB-S-C", mapOf("Size" to "S", "Color" to "Cream")),
                    VariantInfo("M / Cream", "ZOB-M-C", mapOf("Size" to "M", "Color" to "Cream"))
                )
            ),
            ProductData(
                title = "Zara Satin Midi Dress",
                handle = "dev-zara-dress",
                description = "Flowing midi dress featuring a V-neckline and adjustable thin straps. Side slit at hem. Invisible side zip fastening.",
                brand = brands["zara"],
                collections = listOf(collections["womenswear"], collections["sale"]),
                categories = listOf(categories["dresses"]),
                canonicalCategory = clothingCategory,
                thumbnail = "https://images.unsplash.com/photo-1595777457583-95e059d581b8?w=600",
                images = listOf(
                    "https://images.unsplash.com/photo-1595777457583-95e059d581b8?w=800",
                    "https://images.unsplash.com/photo-1572804013309-59a88b7e92f1?w=800"
                ),
                basePrice = BigDecimal("49.99"),
                salePrice = BigDecimal("29.99"),
                variants = listOf(
                    VariantInfo("XS / Emerald", "ZSD-XS-E", mapOf("Size" to "XS", "Color" to "Emerald")),
                    VariantInfo("S / Emerald", "ZSD-S-E", mapOf("Size" to "S", "Color" to "Emerald")),
                    VariantInfo("M / Emerald", "ZSD-M-E", mapOf("Size" to "M", "Color" to "Emerald")),
                    VariantInfo("XS / Black", "ZSD-XS-B", mapOf("Size" to "XS", "Color" to "Black")),
                    VariantInfo("S / Black", "ZSD-S-B", mapOf("Size" to "S", "Color" to "Black"))
                )
            ),

            // H&M products
            ProductData(
                title = "H&M Essential Cotton T-Shirt",
                handle = "dev-hm-tshirt",
                description = "T-shirt in soft cotton jersey with a round neckline and short sleeves. Slightly longer fit for versatile styling.",
                brand = brands["hm"],
                collections = listOf(collections["menswear"], collections["best-sellers"]),
                categories = listOf(categories["t-shirts"]),
                canonicalCategory = clothingCategory,
                thumbnail = "https://images.unsplash.com/photo-1521572163474-6864f9cf17ab?w=600",
                images = listOf(
                    "https://images.unsplash.com/photo-1521572163474-6864f9cf17ab?w=800",
                    "https://images.unsplash.com/photo-1583743814966-8936f5b7be1a?w=800"
                ),
                basePrice = BigDecimal("12.99"),
                variants = listOf(
                    VariantInfo("S / White", "HMT-S-W", mapOf("Size" to "S", "Color" to "White")),
                    VariantInfo("M / White", "HMT-M-W", mapOf("Size" to "M", "Color" to "White")),
                    VariantInfo("L / White", "HMT-L-W", mapOf("Size" to "L", "Color" to "White")),
                    VariantInfo("XL / White", "HMT-XL-W", mapOf("Size" to "XL", "Color" to "White")),
                    VariantInfo("S / Black", "HMT-S-B", mapOf("Size" to "S", "Color" to "Black")),
                    VariantInfo("M / Black", "HMT-M-B", mapOf("Size" to "M", "Color" to "Black")),
                    VariantInfo("L / Black", "HMT-L-B", mapOf("Size" to "L", "Color" to "Black")),
                    VariantInfo("XL / Black", "HMT-XL-B", mapOf("Size" to "XL", "Color" to "Black")),
                    VariantInfo("S / Navy", "HMT-S-N", mapOf("Size" to "S", "Color" to "Navy")),
                    VariantInfo("M / Navy", "HMT-M-N", mapOf("Size" to "M", "Color" to "Navy"))
                )
            ),
            ProductData(
                title = "H&M Slim Fit Chinos",
                handle = "dev-hm-chinos",
                description = "Chinos in washed cotton twill with a zip fly, button, side pockets and welt back pockets. Slim fit.",
                brand = brands["hm"],
                collections = listOf(collections["menswear"]),
                categories = listOf(categories["pants"]),
                canonicalCategory = clothingCategory,
                thumbnail = "https://images.unsplash.com/photo-1624378439575-d8705ad7ae80?w=600",
                images = listOf(
                    "https://images.unsplash.com/photo-1624378439575-d8705ad7ae80?w=800"
                ),
                basePrice = BigDecimal("29.99"),
                variants = listOf(
                    VariantInfo("30 / Beige", "HMC-30-BG", mapOf("Waist" to "30", "Color" to "Beige")),
                    VariantInfo("32 / Beige", "HMC-32-BG", mapOf("Waist" to "32", "Color" to "Beige")),
                    VariantInfo("34 / Beige", "HMC-34-BG", mapOf("Waist" to "34", "Color" to "Beige")),
                    VariantInfo("30 / Navy", "HMC-30-NV", mapOf("Waist" to "30", "Color" to "Navy")),
                    VariantInfo("32 / Navy", "HMC-32-NV", mapOf("Waist" to "32", "Color" to "Navy")),
                    VariantInfo("34 / Navy", "HMC-34-NV", mapOf("Waist" to "34", "Color" to "Navy"))
                )
            ),

            // Balenciaga products
            ProductData(
                title = "Balenciaga Triple S Sneakers",
                handle = "dev-balenciaga-triple-s",
                description = "The Triple S sneaker features a chunky sole and distressed finish. A revolutionary design that became an icon of luxury streetwear.",
                brand = brands["balenciaga"],
                collections = listOf(collections["footwear"], collections["luxury-edit"], collections["new-arrivals"]),
                categories = listOf(categories["sneakers"]),
                canonicalCategory = shoesCategory,
                thumbnail = "https://images.unsplash.com/photo-1539185441755-769473a23570?w=600",
                images = listOf(
                    "https://images.unsplash.com/photo-1539185441755-769473a23570?w=800",
                    "https://images.unsplash.com/photo-1552346154-21d32810aba3?w=800"
                ),
                basePrice = BigDecimal("895.00"),
                variants = listOf(
                    VariantInfo("EU 39 / White", "BTS-39-W", mapOf("Size" to "EU 39", "Color" to "White")),
                    VariantInfo("EU 40 / White", "BTS-40-W", mapOf("Size" to "EU 40", "Color" to "White")),
                    VariantInfo("EU 41 / White", "BTS-41-W", mapOf("Size" to "EU 41", "Color" to "White")),
                    VariantInfo("EU 42 / White", "BTS-42-W", mapOf("Size" to "EU 42", "Color" to "White")),
                    VariantInfo("EU 40 / Black", "BTS-40-B", mapOf("Size" to "EU 40", "Color" to "Black")),
                    VariantInfo("EU 41 / Black", "BTS-41-B", mapOf("Size" to "EU 41", "Color" to "Black"))
                )
            ),
            ProductData(
                title = "Balenciaga Hourglass Bag",
                handle = "dev-balenciaga-hourglass",
                description = "Hourglass small top handle bag in shiny box calfskin. Features the signature curved Hourglass shape with B logo hardware.",
                brand = brands["balenciaga"],
                collections = listOf(collections["accessories"], collections["luxury-edit"], collections["womenswear"]),
                categories = listOf(categories["handbags"]),
                canonicalCategory = bagsCategory,
                thumbnail = "https://images.unsplash.com/photo-1566150905458-1bf1fc113f0d?w=600",
                images = listOf(
                    "https://images.unsplash.com/photo-1566150905458-1bf1fc113f0d?w=800"
                ),
                basePrice = BigDecimal("2190.00"),
                variants = listOf(
                    VariantInfo("Black", "BHB-BLK", mapOf("Color" to "Black")),
                    VariantInfo("Bright Red", "BHB-RED", mapOf("Color" to "Bright Red"))
                )
            ),

            // More Nike products
            ProductData(
                title = "Nike Tech Fleece Hoodie",
                handle = "dev-nike-tech-hoodie",
                description = "The Nike Sportswear Tech Fleece Hoodie is made with premium fleece for lightweight warmth. Its streamlined design provides a modern look.",
                brand = brands["nike"],
                collections = listOf(collections["menswear"], collections["best-sellers"]),
                categories = listOf(categories["jackets"]),
                canonicalCategory = clothingCategory,
                thumbnail = "https://images.unsplash.com/photo-1556821840-3a63f95609a7?w=600",
                images = listOf(
                    "https://images.unsplash.com/photo-1556821840-3a63f95609a7?w=800",
                    "https://images.unsplash.com/photo-1578681994506-b8f463449011?w=800"
                ),
                basePrice = BigDecimal("109.99"),
                variants = listOf(
                    VariantInfo("S / Black", "NTH-S-B", mapOf("Size" to "S", "Color" to "Black")),
                    VariantInfo("M / Black", "NTH-M-B", mapOf("Size" to "M", "Color" to "Black")),
                    VariantInfo("L / Black", "NTH-L-B", mapOf("Size" to "L", "Color" to "Black")),
                    VariantInfo("XL / Black", "NTH-XL-B", mapOf("Size" to "XL", "Color" to "Black")),
                    VariantInfo("S / Grey", "NTH-S-G", mapOf("Size" to "S", "Color" to "Grey")),
                    VariantInfo("M / Grey", "NTH-M-G", mapOf("Size" to "M", "Color" to "Grey")),
                    VariantInfo("L / Grey", "NTH-L-G", mapOf("Size" to "L", "Color" to "Grey"))
                )
            ),

            // More Adidas products
            ProductData(
                title = "Adidas Originals Trefoil Hoodie",
                handle = "dev-adidas-trefoil-hoodie",
                description = "A classic hoodie with the Trefoil logo. Made with soft French terry for all-day comfort. Kangaroo pocket for essentials.",
                brand = brands["adidas"],
                collections = listOf(collections["menswear"]),
                categories = listOf(categories["jackets"]),
                canonicalCategory = clothingCategory,
                thumbnail = "https://images.unsplash.com/photo-1620799140408-edc6dcb6d633?w=600",
                images = listOf(
                    "https://images.unsplash.com/photo-1620799140408-edc6dcb6d633?w=800"
                ),
                basePrice = BigDecimal("69.99"),
                variants = listOf(
                    VariantInfo("S / Black", "ATH-S-B", mapOf("Size" to "S", "Color" to "Black")),
                    VariantInfo("M / Black", "ATH-M-B", mapOf("Size" to "M", "Color" to "Black")),
                    VariantInfo("L / Black", "ATH-L-B", mapOf("Size" to "L", "Color" to "Black")),
                    VariantInfo("S / White", "ATH-S-W", mapOf("Size" to "S", "Color" to "White")),
                    VariantInfo("M / White", "ATH-M-W", mapOf("Size" to "M", "Color" to "White"))
                )
            ),

            // Sandals
            ProductData(
                title = "Nike Benassi Slides",
                handle = "dev-nike-benassi",
                description = "The Nike Benassi Slide offers lightweight cushioning and easy on-off access. Jersey lining for comfort.",
                brand = brands["nike"],
                collections = listOf(collections["footwear"], collections["sale"]),
                categories = listOf(categories["sandals"]),
                canonicalCategory = shoesCategory,
                thumbnail = "https://images.unsplash.com/photo-1603487742131-4160ec999306?w=600",
                images = listOf(
                    "https://images.unsplash.com/photo-1603487742131-4160ec999306?w=800"
                ),
                basePrice = BigDecimal("24.99"),
                salePrice = BigDecimal("19.99"),
                variants = listOf(
                    VariantInfo("UK 6", "NBS-UK6", mapOf("Size" to "UK 6")),
                    VariantInfo("UK 7", "NBS-UK7", mapOf("Size" to "UK 7")),
                    VariantInfo("UK 8", "NBS-UK8", mapOf("Size" to "UK 8")),
                    VariantInfo("UK 9", "NBS-UK9", mapOf("Size" to "UK 9")),
                    VariantInfo("UK 10", "NBS-UK10", mapOf("Size" to "UK 10"))
                )
            ),

            // Gucci clothing
            ProductData(
                title = "Gucci Logo Cotton T-Shirt",
                handle = "dev-gucci-tshirt",
                description = "White cotton T-shirt with Gucci logo print. Relaxed fit with a vintage-inspired look.",
                brand = brands["gucci"],
                collections = listOf(collections["menswear"], collections["luxury-edit"]),
                categories = listOf(categories["t-shirts"]),
                canonicalCategory = clothingCategory,
                thumbnail = "https://images.unsplash.com/photo-1576566588028-4147f3842f27?w=600",
                images = listOf(
                    "https://images.unsplash.com/photo-1576566588028-4147f3842f27?w=800"
                ),
                basePrice = BigDecimal("450.00"),
                variants = listOf(
                    VariantInfo("XS", "GLT-XS", mapOf("Size" to "XS")),
                    VariantInfo("S", "GLT-S", mapOf("Size" to "S")),
                    VariantInfo("M", "GLT-M", mapOf("Size" to "M")),
                    VariantInfo("L", "GLT-L", mapOf("Size" to "L")),
                    VariantInfo("XL", "GLT-XL", mapOf("Size" to "XL"))
                )
            ),

            // Boots
            ProductData(
                title = "Dr. Martens 1460 Boots",
                handle = "dev-dm-1460",
                description = "The original 8-eye boot. Smooth leather upper with the iconic yellow welt stitching. Air-cushioned sole for all-day comfort.",
                brand = brands["hm"], // Using H&M as placeholder since we didn't add Dr. Martens
                collections = listOf(collections["footwear"]),
                categories = listOf(categories["boots"]),
                canonicalCategory = shoesCategory,
                thumbnail = "https://images.unsplash.com/photo-1608256246200-53e635b5b65f?w=600",
                images = listOf(
                    "https://images.unsplash.com/photo-1608256246200-53e635b5b65f?w=800",
                    "https://images.unsplash.com/photo-1605812860427-4024433a70fd?w=800"
                ),
                basePrice = BigDecimal("169.00"),
                variants = listOf(
                    VariantInfo("UK 6", "DM1460-UK6", mapOf("Size" to "UK 6")),
                    VariantInfo("UK 7", "DM1460-UK7", mapOf("Size" to "UK 7")),
                    VariantInfo("UK 8", "DM1460-UK8", mapOf("Size" to "UK 8")),
                    VariantInfo("UK 9", "DM1460-UK9", mapOf("Size" to "UK 9")),
                    VariantInfo("UK 10", "DM1460-UK10", mapOf("Size" to "UK 10"))
                )
            )
        )

        var createdCount = 0
        products.forEach { productData ->
            if (productRepository.findByHandle(productData.handle) == null) {
                createProduct(productData, gbp, eur, usd, ukRegion, euRegion, usRegion)
                createdCount++
            }
        }

        logger.info { "Created $createdCount products with variants and images" }
    }

    private fun createProduct(
        data: ProductData,
        gbp: com.vernont.domain.region.Currency,
        eur: com.vernont.domain.region.Currency,
        usd: com.vernont.domain.region.Currency,
        ukRegion: Region?,
        euRegion: Region?,
        usRegion: Region?
    ) {
        val product = Product().apply {
            title = data.title
            handle = data.handle
            description = data.description
            thumbnail = data.thumbnail
            status = ProductStatus.PUBLISHED
            source = ProductSource.OWNED
            brand = data.brand
            canonicalCategory = data.canonicalCategory
        }

        // Add to collections
        data.collections.filterNotNull().forEach { collection ->
            product.collection = collection
        }

        // Add to categories
        data.categories.filterNotNull().forEach { category ->
            product.categories.add(category)
        }

        // Create options from variant data
        val optionNames = data.variants.flatMap { it.options.keys }.distinct()
        optionNames.forEachIndexed { index, optionName ->
            val option = ProductOption().apply {
                title = optionName
                position = index
                this.product = product
            }
            product.addOption(option)
        }

        // Create variants
        data.variants.forEach { variantInfo ->
            val variant = ProductVariant().apply {
                title = variantInfo.title
                sku = variantInfo.sku
                this.product = product
            }

            // Add variant options
            variantInfo.options.forEach { (optName, optValue) ->
                val productOption = product.options.find { it.title == optName }
                if (productOption != null) {
                    val variantOption = ProductVariantOption().apply {
                        value = optValue
                        option = productOption
                        this.variant = variant
                    }
                    variant.addOption(variantOption)
                }
            }

            // Add prices (GBP as primary, EUR and USD as secondary)
            val priceGbp = ProductVariantPrice().apply {
                amount = data.salePrice ?: data.basePrice
                currencyCode = gbp.code
                regionId = ukRegion?.id
                this.variant = variant
            }
            variant.addPrice(priceGbp)

            val priceEur = ProductVariantPrice().apply {
                amount = ((data.salePrice ?: data.basePrice) * BigDecimal("1.17")).setScale(2, java.math.RoundingMode.HALF_UP)
                currencyCode = eur.code
                regionId = euRegion?.id
                this.variant = variant
            }
            variant.addPrice(priceEur)

            val priceUsd = ProductVariantPrice().apply {
                amount = ((data.salePrice ?: data.basePrice) * BigDecimal("1.27")).setScale(2, java.math.RoundingMode.HALF_UP)
                currencyCode = usd.code
                regionId = usRegion?.id
                this.variant = variant
            }
            variant.addPrice(priceUsd)

            product.addVariant(variant)
        }

        // Add images
        data.images.forEachIndexed { index, imageUrl ->
            val image = ProductImage().apply {
                url = imageUrl
                position = index
                this.product = product
            }
            product.addImage(image)
        }

        productRepository.save(product)
    }

    data class BrandData(val name: String, val slug: String, val tier: BrandTier, val logoUrl: String?)
    data class CollectionData(val title: String, val handle: String, val description: String, val thumbnail: String)
    data class CategoryData(val name: String, val handle: String, val parentHandle: String?)
    data class VariantInfo(val title: String, val sku: String, val options: Map<String, String>)
    data class ProductData(
        val title: String,
        val handle: String,
        val description: String,
        val brand: Brand?,
        val collections: List<ProductCollection?>,
        val categories: List<ProductCategory?>,
        val canonicalCategory: CanonicalCategory?,
        val thumbnail: String,
        val images: List<String>,
        val basePrice: BigDecimal,
        val salePrice: BigDecimal? = null,
        val variants: List<VariantInfo>
    )
}
