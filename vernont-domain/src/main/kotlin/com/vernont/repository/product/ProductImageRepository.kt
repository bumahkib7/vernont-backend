package com.vernont.repository.product

import com.vernont.domain.product.ProductImage
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface ProductImageRepository : JpaRepository<ProductImage, String> {
    fun findByProductId(productId: String): List<ProductImage>
}
