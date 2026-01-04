package com.vernont.api.controller.admin

import com.vernont.application.product.ProductService
import com.vernont.domain.product.dto.ProductVariantResponse
import com.vernont.domain.product.dto.UpdateProductVariantRequest
import com.vernont.repository.product.ProductVariantRepository
import io.github.oshai.kotlinlogging.KotlinLogging
import com.vernont.domain.product.ProductVariant
import org.springframework.data.domain.PageRequest
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import java.util.*

private val variantLogger = KotlinLogging.logger {}

@RestController
@RequestMapping("/admin/variants")
class AdminVariantController(
    private val productService: ProductService,
    private val productVariantRepository: ProductVariantRepository
) {

    @GetMapping
    fun listVariants(
        @RequestParam(name = "_start", defaultValue = "0") start: Int,
        @RequestParam(name = "_end", defaultValue = "10") end: Int,
        @RequestParam(name = "productId", required = false) productId: String?
    ): ResponseEntity<Map<String, Any>> {
        val size = end - start
        val pageIndex = if (size > 0) start / size else 0
        val pageable = PageRequest.of(pageIndex, if (size > 0) size else 10)

        val all: List<ProductVariant> = if (productId.isNullOrBlank()) {
            productVariantRepository.findAll(pageable)
                .content
                .filter { it.deletedAt == null }
        } else {
            productVariantRepository.findAllByProductIdAndDeletedAtIsNull(productId)
        }

        val paged = if (productId.isNullOrBlank()) {
            all
        } else {
            val from = start.coerceAtLeast(0)
            val to = (start + size).coerceAtMost(all.size)
            if (from >= to) emptyList() else all.subList(from, to)
        }

        val content = paged.map { variant -> ProductVariantResponse.from(variant) }
        val total = if (productId.isNullOrBlank()) {
            productVariantRepository.count()
        } else {
            productVariantRepository.countByProductId(productId)
        }

        return ResponseEntity.ok(
            mapOf(
                "content" to content,
                "page" to mapOf("totalElements" to total)
            )
        )
    }

    @GetMapping("/{id}")
    fun getVariant(@PathVariable id: String): ResponseEntity<ProductVariantResponse> {
        val variant = productVariantRepository.findByIdAndDeletedAtIsNull(id)
            ?: return ResponseEntity.notFound().build()
        return ResponseEntity.ok(ProductVariantResponse.from(variant))
    }

    @PutMapping("/{id}")
    fun updateVariant(
        @PathVariable id: String,
        @RequestBody request: UpdateProductVariantRequest
    ): ResponseEntity<ProductVariantResponse> {
        val variant = productVariantRepository.findByIdAndDeletedAtIsNull(id)
            ?: return ResponseEntity.notFound().build()

        val productId = variant.product?.id
            ?: return ResponseEntity.badRequest().build()

        val updated = productService.updateVariant(productId, id, request)
        variantLogger.info { "Updated variant $id for product $productId" }
        return ResponseEntity.ok(updated.variants.first { it.id == id })
    }

    @DeleteMapping("/{id}")
    fun deleteVariant(@PathVariable id: String): ResponseEntity<Void> {
        val variant = productVariantRepository.findByIdAndDeletedAtIsNull(id)
            ?: return ResponseEntity.notFound().build()
        val productId = variant.product?.id ?: return ResponseEntity.badRequest().build()
        productService.deleteVariant(productId, id)
        return ResponseEntity.noContent().build()
    }
}
