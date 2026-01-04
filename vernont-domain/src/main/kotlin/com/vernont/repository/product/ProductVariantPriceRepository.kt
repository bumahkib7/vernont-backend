package com.vernont.repository.product

import com.vernont.domain.product.ProductVariantPrice
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository

@Repository
interface ProductVariantPriceRepository : JpaRepository<ProductVariantPrice, String> {

    @Query(
        """
        SELECT p 
        FROM ProductVariantPrice p
        WHERE p.variant.id IN :variantIds
          AND p.deletedAt IS NULL
          AND LOWER(p.currencyCode) = LOWER(:currencyCode)
          AND (:regionId IS NULL OR p.regionId IS NULL OR p.regionId = :regionId)
        ORDER BY p.regionId NULLS LAST
        """
    )
    fun findActivePrices(
        @Param("variantIds") variantIds: List<String>,
        @Param("currencyCode") currencyCode: String,
        @Param("regionId") regionId: String?
    ): List<ProductVariantPrice>
}
