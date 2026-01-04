package com.vernont.api.service

import com.vernont.domain.product.dto.StoreCalculatedPriceDto
import com.vernont.domain.product.dto.StorePriceDto
import com.vernont.repository.product.ProductVariantPriceRepository
import jakarta.persistence.EntityManager
import org.springframework.stereotype.Service

@Service
class StoreProductService(
    private val entityManager: EntityManager,
    private val productVariantPriceRepository: ProductVariantPriceRepository
) {

    fun getInventoryForVariants(variantIds: List<String>): Map<String, Int> {
        if (variantIds.isEmpty()) return emptyMap()

        val query = entityManager.createNativeQuery(
            """
            SELECT pvi.variant_id, COALESCE(SUM(il.available_quantity), 0) as total_quantity
            FROM product_variant_inventory_item pvi
            LEFT JOIN inventory_level il ON pvi.inventory_item_id = il.inventory_item_id
                AND il.deleted_at IS NULL
            WHERE pvi.variant_id IN (:variantIds)
                AND pvi.deleted_at IS NULL
            GROUP BY pvi.variant_id
            """.trimIndent()
        )
        query.setParameter("variantIds", variantIds)

        @Suppress("UNCHECKED_CAST")
        val results = query.resultList as List<Array<Any>>
        return results.associate { row ->
            row[0].toString() to (row[1] as Number).toInt()
        }
    }

    fun getPricesForVariants(
        variantIds: List<String>,
        currencyCode: String = "usd",
        regionId: String? = null
    ): Map<String, StoreCalculatedPriceDto> {
        if (variantIds.isEmpty()) return emptyMap()

        val prices = productVariantPriceRepository.findActivePrices(variantIds, currencyCode, regionId)
        val pricesByVariant = prices.groupBy { it.variant?.id ?: "" }.filterKeys { it.isNotBlank() }

        return pricesByVariant.mapValues { (variantId, list) ->
            val price = list.first()
            val amount = price.amount.toInt() // stored in minor units already
            val compareAt = price.compareAtPrice?.toInt() ?: amount
            StoreCalculatedPriceDto(
                calculatedAmount = amount,
                originalAmount = compareAt,
                currencyCode = price.currencyCode,
                calculatedPrice = StorePriceDto(
                    id = price.id,
                    amount = amount,
                    currencyCode = price.currencyCode,
                    variantId = variantId
                )
            )
        }
    }
}
