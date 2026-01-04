package com.vernont.domain.store

import com.vernont.domain.common.BaseEntity
import jakarta.persistence.*
import jakarta.validation.constraints.NotBlank

@Entity
@Table(
    name = "store",
    indexes = [
        Index(name = "idx_store_name", columnList = "name"),
        Index(name = "idx_store_default_currency_code", columnList = "default_currency_code"),
        Index(name = "idx_store_deleted_at", columnList = "deleted_at")
    ]
)
@NamedEntityGraph(
    name = "Store.full",
    attributeNodes = [
        NamedAttributeNode("salesChannels"),
        NamedAttributeNode("apiKeys")
    ]
)
@NamedEntityGraph(
    name = "Store.withSalesChannels",
    attributeNodes = [
        NamedAttributeNode("salesChannels")
    ]
)
@NamedEntityGraph(
    name = "Store.withApiKeys",
    attributeNodes = [
        NamedAttributeNode("apiKeys")
    ]
)
class Store : BaseEntity() {

    @NotBlank
    @Column(nullable = false)
    var name: String = ""

    @NotBlank
    @Column(name = "default_currency_code", nullable = false, length = 3)
    var defaultCurrencyCode: String = ""

    @Column(name = "swap_link_template")
    var swapLinkTemplate: String? = null

    @Column(name = "payment_link_template")
    var paymentLinkTemplate: String? = null

    @Column(name = "invite_link_template")
    var inviteLinkTemplate: String? = null

    @Column(name = "default_sales_channel_id")
    var defaultSalesChannelId: String? = null

    @Column(name = "default_region_id")
    var defaultRegionId: String? = null

    @Column(name = "default_location_id")
    var defaultLocationId: String? = null

    @OneToMany(mappedBy = "store", cascade = [CascadeType.ALL], orphanRemoval = true, fetch = FetchType.LAZY)
    var salesChannels: MutableSet<SalesChannel> = mutableSetOf()

    @OneToMany(mappedBy = "store", cascade = [CascadeType.ALL], orphanRemoval = true, fetch = FetchType.LAZY)
    var apiKeys: MutableSet<ApiKey> = mutableSetOf()

    @OneToMany(mappedBy = "store", cascade = [CascadeType.ALL], orphanRemoval = true, fetch = FetchType.LAZY)
    var currencies: MutableSet<StoreCurrency> = mutableSetOf()

    fun addSalesChannel(salesChannel: SalesChannel) {
        salesChannels.add(salesChannel)
        salesChannel.store = this
    }

    fun removeSalesChannel(salesChannel: SalesChannel) {
        salesChannels.remove(salesChannel)
        salesChannel.store = null
    }

    fun addApiKey(apiKey: ApiKey) {
        apiKeys.add(apiKey)
        apiKey.store = this
    }

    fun removeApiKey(apiKey: ApiKey) {
        apiKeys.remove(apiKey)
        apiKey.store = null
    }

    fun addCurrency(currency: StoreCurrency) {
        currencies.add(currency)
        currency.store = this
    }

    fun removeCurrency(currency: StoreCurrency) {
        currencies.remove(currency)
        currency.store = null
    }

    fun updateDefaultCurrency(currencyCode: String) {
        require(currencyCode.length == 3) { "Currency code must be 3 characters" }
        this.defaultCurrencyCode = currencyCode.uppercase()
    }

    fun hasActiveSalesChannels(): Boolean {
        return salesChannels.any { it.isActive }
    }

    fun hasActiveApiKeys(): Boolean {
        return apiKeys.any { !it.revoked }
    }
}
