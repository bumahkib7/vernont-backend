package com.vernont.repository.product

import com.vernont.domain.product.ProductOption
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository

@Repository
interface ProductOptionRepository : JpaRepository<ProductOption, String> {
    fun findByProductId(productId: String): List<ProductOption>

}
