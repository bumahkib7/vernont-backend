package com.vernont.api.controller.store

import com.vernont.api.dto.store.ProductTagListResponse
import com.vernont.api.dto.store.ProductTagResponse
import com.vernont.application.product.ProductTagService
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import java.time.Instant

private val logger = KotlinLogging.logger {}

@RestController
@RequestMapping("/store/product-tags")
class ProductTagController(
    private val productTagService: ProductTagService
) {

    /**
     * GET /store/product-tags/{id}
     * Retrieve a product tag by its ID
     */
    @GetMapping("/{id}")
    fun getProductTag(
        @PathVariable id: String,
        @RequestParam(required = false) fields: String?
    ): ResponseEntity<Map<String, ProductTagResponse>> {
        val tag = productTagService.getTagById(id, fields)
        return ResponseEntity.ok(mapOf("product_tag" to ProductTagResponse.from(tag)))
    }

    /**
     * GET /store/product-tags
     * List product tags with filtering, pagination, and sorting
     */
    @GetMapping
    fun listProductTags(
        @RequestParam(required = false) fields: String?,
        @RequestParam(required = false, defaultValue = "0") offset: Int,
        @RequestParam(required = false, defaultValue = "50") limit: Int,
        @RequestParam(required = false) order: String?,
        @RequestParam(required = false) q: String?,
        @RequestParam(required = false) id: String?,
        @RequestParam(name = "id[]", required = false) ids: List<String>?,
        @RequestParam(required = false) value: String?,
        @RequestParam(name = "value[]", required = false) values: List<String>?,
        @RequestParam(name = "created_at[gte]", required = false) createdAtGte: Instant?,
        @RequestParam(name = "created_at[lte]", required = false) createdAtLte: Instant?,
        @RequestParam(name = "updated_at[gte]", required = false) updatedAtGte: Instant?,
        @RequestParam(name = "updated_at[lte]", required = false) updatedAtLte: Instant?,
        @RequestParam(name = "with_deleted", required = false, defaultValue = "false") withDeleted: Boolean
    ): ResponseEntity<ProductTagListResponse> {
        // Parse order parameter
        val sort = order?.let { parseOrderParameter(it) } ?: Sort.by(Sort.Direction.ASC, "createdAt")
        
        // Create pageable
        val pageable = PageRequest.of(offset / limit, limit, sort)

        // Get tags
        val tagsPage = productTagService.listTags(
            pageable = pageable,
            q = q,
            id = id,
            ids = ids,
            value = value,
            values = values,
            createdAtGte = createdAtGte,
            createdAtLte = createdAtLte,
            updatedAtGte = updatedAtGte,
            updatedAtLte = updatedAtLte,
            withDeleted = withDeleted,
            fields = fields
        )

        val response = ProductTagListResponse(
            limit = limit,
            offset = offset,
            count = tagsPage.totalElements,
            productTags = tagsPage.content.map { ProductTagResponse.from(it) }
        )

        return ResponseEntity.ok(response)
    }

    private fun parseOrderParameter(order: String): Sort {
        val direction = if (order.startsWith("-")) Sort.Direction.DESC else Sort.Direction.ASC
        val field = order.removePrefix("-").removePrefix("+")
        return Sort.by(direction, field)
    }
}
