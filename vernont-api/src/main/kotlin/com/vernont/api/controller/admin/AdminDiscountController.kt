package com.vernont.api.controller.admin

import com.vernont.api.dto.admin.*
import com.vernont.domain.promotion.*
import com.vernont.repository.promotion.DiscountActivityRepository
import com.vernont.repository.promotion.DiscountRedemptionRepository
import com.vernont.repository.promotion.PromotionRepository
import com.vernont.repository.promotion.PromotionRuleRepository
import io.github.oshai.kotlinlogging.KotlinLogging
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.data.domain.PageRequest
import org.springframework.http.ResponseEntity
import org.springframework.messaging.simp.SimpMessagingTemplate
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.bind.annotation.*
import java.math.BigDecimal
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.*

private val logger = KotlinLogging.logger {}

@RestController
@RequestMapping("/admin/discounts")
@Tag(name = "Admin Discounts", description = "Discount and promotion management endpoints")
class AdminDiscountController(
    private val promotionRepository: PromotionRepository,
    private val promotionRuleRepository: PromotionRuleRepository,
    private val discountRedemptionRepository: DiscountRedemptionRepository,
    private val discountActivityRepository: DiscountActivityRepository,
    private val messagingTemplate: SimpMessagingTemplate
) {

    // =========================================================================
    // List & Get
    // =========================================================================

    @Operation(summary = "List all promotions/discounts")
    @GetMapping
    fun listDiscounts(
        @RequestParam(defaultValue = "50") limit: Int,
        @RequestParam(defaultValue = "0") offset: Int,
        @RequestParam(required = false) q: String?,
        @RequestParam(required = false) status: String?,
        @RequestParam(required = false) type: String?
    ): ResponseEntity<PromotionsListResponse> {
        logger.info { "GET /admin/discounts - limit=$limit, offset=$offset, q=$q, status=$status, type=$type" }

        var promotions = promotionRepository.findByDeletedAtIsNull()

        // Filter by search query
        if (!q.isNullOrBlank()) {
            val searchTerm = q.lowercase()
            promotions = promotions.filter { promo ->
                promo.code.lowercase().contains(searchTerm) ||
                promo.name?.lowercase()?.contains(searchTerm) == true ||
                promo.description?.lowercase()?.contains(searchTerm) == true
            }
        }

        // Filter by type
        if (!type.isNullOrBlank()) {
            val typeEnum = try { PromotionType.valueOf(type.uppercase()) } catch (e: Exception) { null }
            if (typeEnum != null) {
                promotions = promotions.filter { it.type == typeEnum }
            }
        }

        // Filter by status
        if (!status.isNullOrBlank()) {
            val now = Instant.now()
            promotions = when (status.uppercase()) {
                "ACTIVE" -> promotions.filter { it.isActive && !it.isDisabled && !it.hasEnded() && it.hasStarted() && !it.hasReachedUsageLimit() }
                "INACTIVE" -> promotions.filter { !it.isActive && !it.isDisabled }
                "SCHEDULED" -> promotions.filter { it.isActive && !it.hasStarted() }
                "EXPIRED" -> promotions.filter { it.hasEnded() }
                "DISABLED" -> promotions.filter { it.isDisabled }
                "LIMIT_REACHED" -> promotions.filter { it.hasReachedUsageLimit() }
                else -> promotions
            }
        }

        // Sort by priority then created date
        promotions = promotions.sortedWith(
            compareByDescending<Promotion> { it.priority }
                .thenByDescending { it.createdAt }
        )

        val count = promotions.size.toLong()
        val paginatedPromotions = promotions.drop(offset).take(limit.coerceAtMost(100))

        // Get redemption stats for each promotion
        val items = paginatedPromotions.map { promo ->
            val redemptionCount = discountRedemptionRepository.countByPromotionId(promo.id)
            val totalDiscount = discountRedemptionRepository.sumDiscountAmountByPromotionId(promo.id) ?: BigDecimal.ZERO
            PromotionListItem.from(promo, redemptionCount, totalDiscount)
        }

        return ResponseEntity.ok(PromotionsListResponse(
            items = items,
            count = count,
            offset = offset,
            limit = limit
        ))
    }

    @Operation(summary = "Get promotion details by ID")
    @GetMapping("/{id}")
    fun getDiscount(@PathVariable id: String): ResponseEntity<PromotionResponse> {
        logger.info { "GET /admin/discounts/$id" }

        val promotion = promotionRepository.findByIdAndDeletedAtIsNull(id)
            ?: return ResponseEntity.notFound().build()

        val rules = promotion.rules.map { PromotionRuleDto.from(it) }
        val stats = getPromotionStats(promotion.id)

        return ResponseEntity.ok(PromotionResponse(
            promotion = PromotionDetail.from(promotion, rules, stats)
        ))
    }

    // =========================================================================
    // Create & Update
    // =========================================================================

    @Operation(summary = "Create a new promotion/discount")
    @PostMapping
    @Transactional
    fun createDiscount(@RequestBody request: CreatePromotionRequest): ResponseEntity<Any> {
        logger.info { "POST /admin/discounts - code=${request.code}" }

        // Validate code is unique
        if (promotionRepository.existsByCode(request.code)) {
            return ResponseEntity.badRequest().body(mapOf(
                "message" to "A promotion with this code already exists"
            ))
        }

        // Validate type
        val promotionType = try {
            PromotionType.valueOf(request.type.uppercase())
        } catch (e: Exception) {
            return ResponseEntity.badRequest().body(mapOf(
                "message" to "Invalid promotion type",
                "validTypes" to PromotionType.entries.map { it.name }
            ))
        }

        // Validate value for percentage type
        if (promotionType == PromotionType.PERCENTAGE && (request.value < 0 || request.value > 100)) {
            return ResponseEntity.badRequest().body(mapOf(
                "message" to "Percentage value must be between 0 and 100"
            ))
        }

        val promotion = Promotion().apply {
            name = request.name
            code = request.code.uppercase()
            type = promotionType
            value = request.value
            description = request.description
            startsAt = request.startsAt
            endsAt = request.endsAt
            usageLimit = request.usageLimit
            customerUsageLimit = request.customerUsageLimit ?: 1
            minimumAmount = request.minimumAmount
            maximumDiscount = request.maximumDiscount
            isStackable = request.isStackable ?: false
            priority = request.priority ?: 0
            buyQuantity = request.buyQuantity
            getQuantity = request.getQuantity
            getDiscountValue = request.getDiscountValue
            isActive = request.activateImmediately ?: false
        }

        val savedPromotion = promotionRepository.save(promotion)

        // Add rules if provided
        request.rules?.forEach { ruleReq ->
            val ruleType = try {
                PromotionRuleType.valueOf(ruleReq.type.uppercase())
            } catch (e: Exception) { null }

            if (ruleType != null) {
                val rule = PromotionRule().apply {
                    this.promotion = savedPromotion
                    this.type = ruleType
                    this.value = ruleReq.value
                    this.description = ruleReq.description
                    this.attribute = ruleReq.attribute
                    this.operator = ruleReq.operator
                }
                savedPromotion.addRule(rule)
            }
        }

        promotionRepository.save(savedPromotion)

        // Log activity
        logActivity(savedPromotion, DiscountActivityType.PROMOTION_CREATED, "Promotion '${savedPromotion.code}' created")

        // Publish WebSocket event
        publishDiscountEvent("PROMOTION_CREATED", savedPromotion)

        val rules = savedPromotion.rules.map { PromotionRuleDto.from(it) }
        val stats = getPromotionStats(savedPromotion.id)

        return ResponseEntity.status(201).body(PromotionResponse(
            promotion = PromotionDetail.from(savedPromotion, rules, stats)
        ))
    }

    @Operation(summary = "Update a promotion/discount")
    @PutMapping("/{id}")
    @Transactional
    fun updateDiscount(
        @PathVariable id: String,
        @RequestBody request: UpdatePromotionRequest
    ): ResponseEntity<Any> {
        logger.info { "PUT /admin/discounts/$id" }

        val promotion = promotionRepository.findByIdAndDeletedAtIsNull(id)
            ?: return ResponseEntity.notFound().build()

        // Update fields if provided
        request.name?.let { promotion.name = it }
        request.description?.let { promotion.description = it }
        request.value?.let {
            if (promotion.type == PromotionType.PERCENTAGE && (it < 0 || it > 100)) {
                return ResponseEntity.badRequest().body(mapOf(
                    "message" to "Percentage value must be between 0 and 100"
                ))
            }
            promotion.value = it
        }
        request.startsAt?.let { promotion.startsAt = it }
        request.endsAt?.let { promotion.endsAt = it }
        request.usageLimit?.let { promotion.usageLimit = it }
        request.customerUsageLimit?.let { promotion.customerUsageLimit = it }
        request.minimumAmount?.let { promotion.minimumAmount = it }
        request.maximumDiscount?.let { promotion.maximumDiscount = it }
        request.isStackable?.let { promotion.isStackable = it }
        request.priority?.let { promotion.priority = it }
        request.buyQuantity?.let { promotion.buyQuantity = it }
        request.getQuantity?.let { promotion.getQuantity = it }
        request.getDiscountValue?.let { promotion.getDiscountValue = it }

        // Update rules if provided
        request.rules?.let { newRules ->
            // Remove old rules
            promotion.rules.forEach { it.softDelete() }
            promotion.rules.clear()

            // Add new rules
            newRules.forEach { ruleReq ->
                val ruleType = try {
                    PromotionRuleType.valueOf(ruleReq.type.uppercase())
                } catch (e: Exception) { null }

                if (ruleType != null) {
                    val rule = PromotionRule().apply {
                        this.promotion = promotion
                        this.type = ruleType
                        this.value = ruleReq.value
                        this.description = ruleReq.description
                        this.attribute = ruleReq.attribute
                        this.operator = ruleReq.operator
                    }
                    promotion.addRule(rule)
                }
            }
        }

        val savedPromotion = promotionRepository.save(promotion)

        // Log activity
        logActivity(savedPromotion, DiscountActivityType.PROMOTION_UPDATED, "Promotion '${savedPromotion.code}' updated")

        // Publish WebSocket event
        publishDiscountEvent("PROMOTION_UPDATED", savedPromotion)

        val rules = savedPromotion.rules.filter { it.deletedAt == null }.map { PromotionRuleDto.from(it) }
        val stats = getPromotionStats(savedPromotion.id)

        return ResponseEntity.ok(PromotionResponse(
            promotion = PromotionDetail.from(savedPromotion, rules, stats)
        ))
    }

    // =========================================================================
    // Delete & Status Changes
    // =========================================================================

    @Operation(summary = "Delete a promotion/discount")
    @DeleteMapping("/{id}")
    @Transactional
    fun deleteDiscount(@PathVariable id: String): ResponseEntity<Any> {
        logger.info { "DELETE /admin/discounts/$id" }

        val promotion = promotionRepository.findByIdAndDeletedAtIsNull(id)
            ?: return ResponseEntity.notFound().build()

        promotion.softDelete()
        promotionRepository.save(promotion)

        // Log activity
        logActivity(promotion, DiscountActivityType.PROMOTION_DELETED, "Promotion '${promotion.code}' deleted")

        // Publish WebSocket event
        publishDiscountEvent("PROMOTION_DELETED", promotion)

        return ResponseEntity.ok(mapOf(
            "message" to "Promotion deleted",
            "id" to id
        ))
    }

    @Operation(summary = "Activate a promotion")
    @PostMapping("/{id}/activate")
    @Transactional
    fun activateDiscount(@PathVariable id: String): ResponseEntity<Any> {
        logger.info { "POST /admin/discounts/$id/activate" }

        val promotion = promotionRepository.findByIdAndDeletedAtIsNull(id)
            ?: return ResponseEntity.notFound().build()

        promotion.activate()
        val savedPromotion = promotionRepository.save(promotion)

        // Log activity
        logActivity(savedPromotion, DiscountActivityType.PROMOTION_ACTIVATED, "Promotion '${savedPromotion.code}' activated")

        // Publish WebSocket event
        publishDiscountEvent("PROMOTION_ACTIVATED", savedPromotion)

        val rules = savedPromotion.rules.filter { it.deletedAt == null }.map { PromotionRuleDto.from(it) }
        val stats = getPromotionStats(savedPromotion.id)

        return ResponseEntity.ok(PromotionResponse(
            promotion = PromotionDetail.from(savedPromotion, rules, stats)
        ))
    }

    @Operation(summary = "Deactivate a promotion")
    @PostMapping("/{id}/deactivate")
    @Transactional
    fun deactivateDiscount(@PathVariable id: String): ResponseEntity<Any> {
        logger.info { "POST /admin/discounts/$id/deactivate" }

        val promotion = promotionRepository.findByIdAndDeletedAtIsNull(id)
            ?: return ResponseEntity.notFound().build()

        promotion.deactivate()
        val savedPromotion = promotionRepository.save(promotion)

        // Log activity
        logActivity(savedPromotion, DiscountActivityType.PROMOTION_DEACTIVATED, "Promotion '${savedPromotion.code}' deactivated")

        // Publish WebSocket event
        publishDiscountEvent("PROMOTION_DEACTIVATED", savedPromotion)

        val rules = savedPromotion.rules.filter { it.deletedAt == null }.map { PromotionRuleDto.from(it) }
        val stats = getPromotionStats(savedPromotion.id)

        return ResponseEntity.ok(PromotionResponse(
            promotion = PromotionDetail.from(savedPromotion, rules, stats)
        ))
    }

    // =========================================================================
    // Bulk Operations
    // =========================================================================

    @Operation(summary = "Perform bulk operations on promotions")
    @PostMapping("/bulk")
    @Transactional
    fun bulkAction(@RequestBody request: BulkDiscountRequest): ResponseEntity<BulkDiscountResult> {
        logger.info { "POST /admin/discounts/bulk - action=${request.action}, ids=${request.ids.size}" }

        val errors = mutableListOf<BulkDiscountError>()
        var successCount = 0

        for (promotionId in request.ids) {
            try {
                val promotion = promotionRepository.findByIdAndDeletedAtIsNull(promotionId)
                    ?: throw IllegalArgumentException("Promotion not found")

                when (request.action.uppercase()) {
                    "ACTIVATE" -> {
                        promotion.activate()
                        logActivity(promotion, DiscountActivityType.PROMOTION_ACTIVATED, "Promotion '${promotion.code}' activated (bulk)")
                    }
                    "DEACTIVATE" -> {
                        promotion.deactivate()
                        logActivity(promotion, DiscountActivityType.PROMOTION_DEACTIVATED, "Promotion '${promotion.code}' deactivated (bulk)")
                    }
                    "DELETE" -> {
                        promotion.softDelete()
                        logActivity(promotion, DiscountActivityType.PROMOTION_DELETED, "Promotion '${promotion.code}' deleted (bulk)")
                    }
                    else -> throw IllegalArgumentException("Invalid action: ${request.action}")
                }

                promotionRepository.save(promotion)
                successCount++

            } catch (e: Exception) {
                logger.error(e) { "Failed to perform bulk action on promotion $promotionId" }
                errors.add(BulkDiscountError(promotionId, e.message ?: "Unknown error"))
            }
        }

        // Publish bulk action event
        publishBulkActionEvent(request.action, successCount)

        return ResponseEntity.ok(BulkDiscountResult(
            successCount = successCount,
            failureCount = errors.size,
            errors = errors
        ))
    }

    // =========================================================================
    // Duplicate
    // =========================================================================

    @Operation(summary = "Duplicate a promotion")
    @PostMapping("/{id}/duplicate")
    @Transactional
    fun duplicateDiscount(@PathVariable id: String): ResponseEntity<Any> {
        logger.info { "POST /admin/discounts/$id/duplicate" }

        val original = promotionRepository.findByIdAndDeletedAtIsNull(id)
            ?: return ResponseEntity.notFound().build()

        // Generate unique code
        var newCode = "${original.code}_COPY"
        var counter = 1
        while (promotionRepository.existsByCode(newCode)) {
            newCode = "${original.code}_COPY_$counter"
            counter++
        }

        val duplicate = Promotion().apply {
            name = original.name?.let { "$it (Copy)" }
            code = newCode
            type = original.type
            value = original.value
            description = original.description
            startsAt = original.startsAt
            endsAt = original.endsAt
            usageLimit = original.usageLimit
            customerUsageLimit = original.customerUsageLimit
            minimumAmount = original.minimumAmount
            maximumDiscount = original.maximumDiscount
            isStackable = original.isStackable
            priority = original.priority
            buyQuantity = original.buyQuantity
            getQuantity = original.getQuantity
            getDiscountValue = original.getDiscountValue
            isActive = false // Start as inactive
        }

        val savedDuplicate = promotionRepository.save(duplicate)

        // Copy rules
        original.rules.filter { it.deletedAt == null }.forEach { originalRule ->
            val ruleCopy = PromotionRule().apply {
                this.promotion = savedDuplicate
                this.type = originalRule.type
                this.value = originalRule.value
                this.description = originalRule.description
                this.attribute = originalRule.attribute
                this.operator = originalRule.operator
            }
            savedDuplicate.addRule(ruleCopy)
        }

        promotionRepository.save(savedDuplicate)

        // Log activity
        logActivity(savedDuplicate, DiscountActivityType.PROMOTION_DUPLICATED, "Promotion '${savedDuplicate.code}' created as copy of '${original.code}'")

        val rules = savedDuplicate.rules.map { PromotionRuleDto.from(it) }
        val stats = getPromotionStats(savedDuplicate.id)

        return ResponseEntity.status(201).body(PromotionResponse(
            promotion = PromotionDetail.from(savedDuplicate, rules, stats)
        ))
    }

    // =========================================================================
    // Stats & Analytics
    // =========================================================================

    @Operation(summary = "Get discount statistics")
    @GetMapping("/stats")
    fun getStats(): ResponseEntity<DiscountStatsResponse> {
        logger.info { "GET /admin/discounts/stats" }

        val now = Instant.now()
        val today = now.truncatedTo(ChronoUnit.DAYS)
        val weekAgo = now.minus(7, ChronoUnit.DAYS)

        val allPromotions = promotionRepository.findByDeletedAtIsNull()

        val activeCount = allPromotions.count { promo ->
            promo.isActive && !promo.isDisabled && !promo.hasEnded() && promo.hasStarted() && !promo.hasReachedUsageLimit()
        }
        val scheduledCount = allPromotions.count { promo ->
            promo.isActive && !promo.hasStarted()
        }
        val expiredCount = allPromotions.count { it.hasEnded() }
        val disabledCount = allPromotions.count { it.isDisabled }

        val totalRedemptions = discountRedemptionRepository.count()
        val totalDiscountGiven = discountRedemptionRepository.sumDiscountAmountSince(Instant.EPOCH) ?: BigDecimal.ZERO
        val redemptionsToday = discountRedemptionRepository.countRedemptionsSince(today)
        val redemptionsThisWeek = discountRedemptionRepository.countRedemptionsSince(weekAgo)

        // Get top performing codes (simplified - would need proper query in production)
        val topCodes = allPromotions
            .filter { it.usageCount > 0 }
            .sortedByDescending { it.usageCount }
            .take(5)
            .map { promo ->
                val totalDiscount = discountRedemptionRepository.sumDiscountAmountByPromotionId(promo.id) ?: BigDecimal.ZERO
                TopPerformingCode(
                    promotionId = promo.id,
                    code = promo.code,
                    redemptionCount = promo.usageCount.toLong(),
                    totalDiscount = totalDiscount
                )
            }

        return ResponseEntity.ok(DiscountStatsResponse(
            totalPromotions = allPromotions.size.toLong(),
            activePromotions = activeCount.toLong(),
            scheduledPromotions = scheduledCount.toLong(),
            expiredPromotions = expiredCount.toLong(),
            disabledPromotions = disabledCount.toLong(),
            totalRedemptions = totalRedemptions,
            totalDiscountGiven = totalDiscountGiven,
            redemptionsToday = redemptionsToday,
            redemptionsThisWeek = redemptionsThisWeek,
            topPerformingCodes = topCodes
        ))
    }

    // =========================================================================
    // Redemptions
    // =========================================================================

    @Operation(summary = "Get redemption history for a promotion")
    @GetMapping("/{id}/redemptions")
    fun getRedemptions(
        @PathVariable id: String,
        @RequestParam(defaultValue = "50") limit: Int,
        @RequestParam(defaultValue = "0") offset: Int
    ): ResponseEntity<RedemptionsResponse> {
        logger.info { "GET /admin/discounts/$id/redemptions" }

        val promotion = promotionRepository.findByIdAndDeletedAtIsNull(id)
            ?: return ResponseEntity.notFound().build()

        val pageable = PageRequest.of(offset / limit.coerceAtLeast(1), limit.coerceAtMost(100))
        val redemptions = discountRedemptionRepository.findByPromotionId(id, pageable)

        return ResponseEntity.ok(RedemptionsResponse(
            items = redemptions.content.map { RedemptionListItem.from(it) },
            count = redemptions.totalElements,
            offset = offset,
            limit = limit
        ))
    }

    // =========================================================================
    // Activity Feed
    // =========================================================================

    @Operation(summary = "Get recent discount activity")
    @GetMapping("/activity")
    fun getActivity(
        @RequestParam(defaultValue = "20") limit: Int
    ): ResponseEntity<DiscountActivityResponse> {
        logger.info { "GET /admin/discounts/activity" }

        val pageable = PageRequest.of(0, limit.coerceAtMost(50))
        val activities = discountActivityRepository.findRecentActivity(pageable)

        return ResponseEntity.ok(DiscountActivityResponse(
            items = activities.content.map { DiscountActivityItem.from(it) },
            count = activities.totalElements
        ))
    }

    // =========================================================================
    // Code Generation
    // =========================================================================

    @Operation(summary = "Generate a unique discount code")
    @PostMapping("/generate-code")
    fun generateCode(): ResponseEntity<GeneratedCodeResponse> {
        logger.info { "POST /admin/discounts/generate-code" }

        var code: String
        var attempts = 0
        do {
            code = generateRandomCode()
            attempts++
        } while (promotionRepository.existsByCode(code) && attempts < 10)

        return ResponseEntity.ok(GeneratedCodeResponse(code = code))
    }

    // =========================================================================
    // Helper Methods
    // =========================================================================

    private fun getPromotionStats(promotionId: String): PromotionStats {
        val now = Instant.now()
        val today = now.truncatedTo(ChronoUnit.DAYS)
        val weekAgo = now.minus(7, ChronoUnit.DAYS)

        val redemptionCount = discountRedemptionRepository.countByPromotionId(promotionId)
        val totalDiscount = discountRedemptionRepository.sumDiscountAmountByPromotionId(promotionId) ?: BigDecimal.ZERO

        // Simplified stats - would need proper queries in production
        return PromotionStats(
            redemptionCount = redemptionCount,
            totalDiscountGiven = totalDiscount,
            averageOrderValue = null,
            redemptionsToday = 0,
            redemptionsThisWeek = 0
        )
    }

    private fun logActivity(
        promotion: Promotion?,
        activityType: DiscountActivityType,
        description: String
    ) {
        try {
            val activity = DiscountActivity.create(
                promotion = promotion,
                activityType = activityType,
                description = description,
                actorId = null, // TODO: Get from security context
                actorName = "Admin"
            )
            discountActivityRepository.save(activity)
        } catch (e: Exception) {
            logger.warn(e) { "Failed to log discount activity" }
        }
    }

    private fun publishDiscountEvent(eventType: String, promotion: Promotion) {
        try {
            val event: Any = mapOf(
                "type" to eventType,
                "promotionId" to promotion.id,
                "promotionCode" to promotion.code,
                "message" to "$eventType: ${promotion.code}",
                "timestamp" to Instant.now().toString()
            )
            messagingTemplate.convertAndSend("/topic/discounts", event)
        } catch (e: Exception) {
            logger.warn(e) { "Failed to publish discount event" }
        }
    }

    private fun publishBulkActionEvent(action: String, count: Int) {
        try {
            val event: Any = mapOf(
                "type" to "BULK_ACTION",
                "action" to action,
                "count" to count,
                "timestamp" to Instant.now().toString()
            )
            messagingTemplate.convertAndSend("/topic/discounts", event)
        } catch (e: Exception) {
            logger.warn(e) { "Failed to publish bulk action event" }
        }
    }

    private fun generateRandomCode(): String {
        val chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"
        val prefix = listOf("SAVE", "DEAL", "PROMO", "OFF", "VIP").random()
        val suffix = (1..6).map { chars.random() }.joinToString("")
        return "$prefix$suffix"
    }
}
