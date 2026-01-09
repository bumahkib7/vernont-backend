package com.vernont.domain.product

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonManagedReference
import com.fasterxml.jackson.annotation.JsonValue
import com.vernont.domain.common.BaseEntity
import jakarta.persistence.*
import jakarta.validation.constraints.NotBlank

@Entity
@Table(
    name = "product",
    indexes = [
        Index(name = "idx_product_handle", columnList = "handle", unique = true),
        Index(name = "idx_product_status", columnList = "status"),
        Index(name = "idx_product_collection_id", columnList = "collection_id"),
        Index(name = "idx_product_type_id", columnList = "type_id"),
        Index(name = "idx_product_deleted_at", columnList = "deleted_at"),
        Index(name = "idx_product_extracted_size", columnList = "extracted_size"),
        Index(name = "idx_product_size_type", columnList = "size_type")
    ]
)
@EntityListeners(com.vernont.domain.product.listener.ProductSizeExtractionListener::class)
@NamedEntityGraph(
    name = "Product.full",
    attributeNodes = [
        NamedAttributeNode("variants"),
        NamedAttributeNode("images"),
        NamedAttributeNode("options"),
        NamedAttributeNode("tags"),
        NamedAttributeNode("categories"),
        NamedAttributeNode("collection"),
        NamedAttributeNode("type"),
        NamedAttributeNode("brand")
    ]
)
@NamedEntityGraph(
    name = "Product.withVariants",
    attributeNodes = [
        NamedAttributeNode(value = "variants", subgraph = "variantSubgraph")
    ],
    subgraphs = [
        NamedSubgraph(
            name = "variantSubgraph",
            attributeNodes = [
                NamedAttributeNode("prices"),
                NamedAttributeNode("options")
            ]
        )
    ]
)
@NamedEntityGraph(
    name = "Product.summary",
    attributeNodes = [
        NamedAttributeNode("images")
    ]
)
class Product : BaseEntity() {

    @NotBlank
    @Column(nullable = false)
    var title: String = ""

    @NotBlank
    @Column(nullable = false, unique = true)
    var handle: String = ""

    @Column
    var subtitle: String? = null

    @Column(columnDefinition = "TEXT")
    var description: String? = null

    @Column(nullable = false)
    var isGiftcard: Boolean = false

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    var status: ProductStatus = ProductStatus.DRAFT

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    var source: ProductSource = ProductSource.OWNED

    @Column
    var thumbnail: String? = null

    @Column
    var weight: String? = null

    @Column
    var length: String? = null

    @Column
    var height: String? = null

    @Column
    var width: String? = null

    @Column
    var originCountry: String? = null

    @Column
    var hsCode: String? = null

    @Column
    var midCode: String? = null

    @Column
    var material: String? = null

    /**
     * Extracted size from product title (e.g., "40", "10", "8.5")
     * Automatically populated from title on save via EntityListener
     */
    @Column(name = "extracted_size")
    var extractedSize: String? = null

    /**
     * Size type/unit (EU, UK, US, NUMERIC)
     */
    @Column(name = "size_type")
    var sizeType: String? = null

    @Column(nullable = false)
    var discountable: Boolean = true

    @Column
    var externalId: String? = null

    /**
     * Key used to match against the external network.
     * For AWIN this could be their product id or a hash of merchant+sku+url.
     */
    @Column(name = "external_key", unique = false)
    var externalKey: String? = null

    /**
     * Optional “native” product URL at the merchant (without tracking params).
     */
    @Column(name = "canonical_url")
    var canonicalUrl: String? = null

    @Column(name = "shipping_profile_id")
    var shippingProfileId: String? = null

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "collection_id")
    var collection: ProductCollection? = null

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "type_id")
    var type: ProductType? = null

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "brand_id")
    var brand: Brand? = null

    @OneToMany(mappedBy = "product", cascade = [CascadeType.ALL], orphanRemoval = true, fetch = FetchType.LAZY)
    @JsonManagedReference
    @org.hibernate.annotations.BatchSize(size = 25)
    var variants: MutableSet<ProductVariant> = mutableSetOf()

    @OneToMany(mappedBy = "product", cascade = [CascadeType.ALL], orphanRemoval = true, fetch = FetchType.LAZY)
    @org.hibernate.annotations.BatchSize(size = 25)
    var images: MutableSet<ProductImage> = mutableSetOf()

    @OneToMany(mappedBy = "product", cascade = [CascadeType.ALL], orphanRemoval = true, fetch = FetchType.LAZY)
    @org.hibernate.annotations.BatchSize(size = 25)
    var options: MutableSet<ProductOption> = mutableSetOf()

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
        name = "product_tags",
        joinColumns = [JoinColumn(name = "product_id")],
        inverseJoinColumns = [JoinColumn(name = "tag_id")]
    )
    @org.hibernate.annotations.BatchSize(size = 25)
    var tags: MutableSet<ProductTag> = mutableSetOf()

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
        name = "product_category_product",
        joinColumns = [JoinColumn(name = "product_id")],
        inverseJoinColumns = [JoinColumn(name = "product_category_id")]
    )
    @org.hibernate.annotations.BatchSize(size = 25)
    var categories: MutableSet<ProductCategory> = mutableSetOf()

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "canonical_category_id")
    var canonicalCategory: CanonicalCategory? = null

    fun addVariant(variant: ProductVariant) {
        variants.add(variant)
        variant.product = this
    }

    fun removeVariant(variant: ProductVariant) {
        variants.remove(variant)
        variant.product = null
    }

    fun addImage(image: ProductImage) {
        images.add(image)
        image.product = this
    }

    fun addOption(option: ProductOption) {
        options.add(option)
        option.product = this
    }

    fun publish() {
        this.status = ProductStatus.PUBLISHED
    }

    fun draft() {
        this.status = ProductStatus.DRAFT
    }

    fun isPublished(): Boolean = status == ProductStatus.PUBLISHED
}

enum class ProductStatus {
    /**
     * Initial state - product is being drafted
     */
    DRAFT,

    /**
     * Core data persisted, waiting for images/assets to upload
     */
    PENDING_ASSETS,

    /**
     * Product proposed for review (affiliate flow)
     */
    PROPOSED,

    /**
     * All assets ready, product can be published
     */
    READY,

    /**
     * Product is live on storefront
     */
    PUBLISHED,

    /**
     * Product was rejected (affiliate flow)
     */
    REJECTED,

    /**
     * Product creation failed, awaiting cleanup
     */
    FAILED,

    /**
     * Product is archived/soft-deleted
     */
    ARCHIVED;

    @JsonValue
    fun toJson(): String = name.lowercase()

    companion object {
        @JsonCreator
        @JvmStatic
        fun fromJson(value: String): ProductStatus = valueOf(value.uppercase())

        /**
         * States that are terminal (product won't change automatically)
         */
        val TERMINAL_STATES = setOf(PUBLISHED, REJECTED, ARCHIVED)

        /**
         * States where product is visible to customers
         */
        val VISIBLE_STATES = setOf(PUBLISHED)

        /**
         * States where product can be edited
         */
        val EDITABLE_STATES = setOf(DRAFT, PENDING_ASSETS, READY, PROPOSED)
    }

    fun isVisible(): Boolean = this in VISIBLE_STATES
    fun isEditable(): Boolean = this in EDITABLE_STATES
    fun isTerminal(): Boolean = this in TERMINAL_STATES
}


enum class ProductSource {
    OWNED
}
