package com.vernont.api.controller.admin

import com.vernont.api.dto.admin.*
import com.vernont.domain.pricing.*
import com.vernont.repository.pricing.PriceChangeLogRepository
import com.vernont.repository.pricing.PricingRuleRepository
import com.vernont.repository.product.ProductRepository
import com.vernont.repository.product.ProductVariantRepository
import com.vernont.repository.product.ProductVariantPriceRepository
import io.github.oshai.kotlinlogging.KotlinLogging
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.http.ResponseEntity
import org.springframework.messaging.simp.SimpMessagingTemplate
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.bind.annotation.*
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit

private val logger = KotlinLogging.logger {}

@RestController
@RequestMapping("/admin/pricing")
@Tag(name = "Admin Pricing", description = "Pricing workbench and management endpoints")
class AdminPricingController(
    private val productRepository: ProductRepository,
    private val productVariantRepository: ProductVariantRepository,
    private val productVariantPriceRepository: ProductVariantPriceRepository,
    private val pricingRuleRepository: PricingRuleRepository,
    private val priceChangeLogRepository: PriceChangeLogRepository,
    private val messagingTemplate: SimpMessagingTemplate
) {

    // =========================================================================
    // Workbench
    // =========================================================================

    @Operation(summary = "Get pricing workbench data")
    @GetMapping("/workbench")
    fun getWorkbench(
        @RequestParam(defaultValue = "50") limit: Int,
        @RequestParam(defaultValue = "0") offset: Int,
        @RequestParam(required = false) q: String?,
        @RequestParam(required = false) categoryId: String?,
        @RequestParam(required = false) hasDiscount: Boolean?
    ): ResponseEntity<WorkbenchResponse> {
        logger.info { "GET /admin/pricing/workbench - limit=$limit, offset=$offset, q=$q" }

        // Get all variants with their products and prices
        val allVariants = productVariantRepository.findByDeletedAtIsNull()

        // Filter by search query
        var filteredVariants = if (!q.isNullOrBlank()) {
            val searchTerm = q.lowercase()
            allVariants.filter { variant ->
                variant.title.lowercase().contains(searchTerm) ||
                variant.sku?.lowercase()?.contains(searchTerm) == true ||
                variant.product?.title?.lowercase()?.contains(searchTerm) == true
            }
        } else {
            allVariants
        }

        // Filter by hasDiscount
        if (hasDiscount == true) {
            filteredVariants = filteredVariants.filter { variant ->
                variant.prices.any { price ->
                    price.compareAtPrice != null && price.compareAtPrice!! > price.amount
                }
            }
        }

        val count = filteredVariants.size.toLong()
        val paginatedVariants = filteredVariants.drop(offset).take(limit.coerceAtMost(100))

        val items = paginatedVariants.mapNotNull { variant ->
            val product = variant.product ?: return@mapNotNull null
            val price = variant.prices.firstOrNull { it.currencyCode.equals("GBP", ignoreCase = true) && it.deletedAt == null }

            val currentPriceCents = price?.amount?.multiply(BigDecimal(100))?.toInt() ?: 0
            val compareAtCents = price?.compareAtPrice?.multiply(BigDecimal(100))?.toInt()

            WorkbenchItem(
                variantId = variant.id,
                productId = product.id,
                productTitle = product.title,
                variantTitle = variant.title,
                sku = variant.sku,
                barcode = variant.barcode,
                thumbnail = product.thumbnail,
                currentPrice = currentPriceCents,
                compareAtPrice = compareAtCents,
                currencyCode = "GBP",
                costPrice = null, // Could be added if cost tracking is implemented
                margin = null,
                marginPercentage = null,
                inventory = null, // Inventory quantities fetched from external system
                lastUpdated = variant.updatedAt.atOffset(ZoneOffset.UTC),
                lastUpdatedBy = variant.updatedBy
            )
        }

        // Get stats
        val activeRulesCount = pricingRuleRepository.findActiveRules().size
        val variantsWithDiscount = allVariants.count { variant ->
            variant.prices.any { price -> price.compareAtPrice != null && price.compareAtPrice!! > price.amount }
        }

        val stats = WorkbenchStats(
            totalVariants = count,
            variantsWithDiscount = variantsWithDiscount.toLong(),
            averageMargin = null,
            activeRules = activeRulesCount
        )

        return ResponseEntity.ok(WorkbenchResponse(
            items = items,
            count = count,
            offset = offset,
            limit = limit,
            stats = stats
        ))
    }

    // =========================================================================
    // Bulk Update
    // =========================================================================

    @Operation(summary = "Bulk update prices")
    @PostMapping("/bulk-update")
    @Transactional
    fun bulkUpdatePrices(@RequestBody request: BulkPriceUpdateRequest): ResponseEntity<BulkPriceUpdateResult> {
        logger.info { "POST /admin/pricing/bulk-update - ${request.updates.size} updates" }

        val successes = mutableListOf<PriceChangeLog>()
        val errors = mutableListOf<BulkUpdateError>()

        for (update in request.updates) {
            try {
                val variant = productVariantRepository.findByIdAndDeletedAtIsNull(update.variantId)
                    ?: throw IllegalArgumentException("Variant not found: ${update.variantId}")

                val product = variant.product
                    ?: throw IllegalArgumentException("Product not found for variant: ${update.variantId}")

                // Find or create GBP price
                var price = variant.prices.firstOrNull {
                    it.currencyCode.equals("GBP", ignoreCase = true) && it.deletedAt == null
                }

                val previousAmount = price?.amount
                val newAmount = BigDecimal(update.amount).divide(BigDecimal(100), 4, RoundingMode.HALF_UP)
                val newCompareAt = update.compareAtPrice?.let {
                    BigDecimal(it).divide(BigDecimal(100), 4, RoundingMode.HALF_UP)
                }

                if (price != null) {
                    price.amount = newAmount
                    price.compareAtPrice = newCompareAt
                } else {
                    price = com.vernont.domain.product.ProductVariantPrice().apply {
                        this.variant = variant
                        this.currencyCode = "GBP"
                        this.amount = newAmount
                        this.compareAtPrice = newCompareAt
                    }
                    variant.prices.add(price)
                }

                productVariantRepository.save(variant)

                // Log the change
                val changeLog = PriceChangeLog.createBulkUpdate(
                    variantId = variant.id,
                    productId = product.id,
                    previousAmount = previousAmount,
                    newAmount = newAmount,
                    changedBy = "admin", // TODO: Get from security context
                    productTitle = product.title,
                    variantTitle = variant.title
                ).apply {
                    sku = variant.sku
                    newCompareAt?.let { previousCompareAt = price.compareAtPrice }
                    this.newCompareAt = newCompareAt
                }

                priceChangeLogRepository.save(changeLog)
                successes.add(changeLog)

                // Publish WebSocket event
                publishPriceChangeEvent(changeLog, product.title)

            } catch (e: Exception) {
                logger.error(e) { "Failed to update price for variant ${update.variantId}" }
                errors.add(BulkUpdateError(update.variantId, e.message ?: "Unknown error"))
            }
        }

        // Publish bulk update event
        if (successes.isNotEmpty()) {
            publishBulkUpdateEvent(successes.size)
        }

        return ResponseEntity.ok(BulkPriceUpdateResult(
            successCount = successes.size,
            failureCount = errors.size,
            errors = errors,
            changes = successes.map { PriceChangeLogDto.from(it) }
        ))
    }

    @Operation(summary = "Preview bulk price update (dry run)")
    @PostMapping("/bulk-update/preview")
    fun previewBulkUpdate(@RequestBody request: BulkPriceUpdateRequest): ResponseEntity<List<SimulatedPriceChange>> {
        val previews = request.updates.mapNotNull { update ->
            val variant = productVariantRepository.findByIdAndDeletedAtIsNull(update.variantId)
                ?: return@mapNotNull null
            val product = variant.product ?: return@mapNotNull null

            val currentPrice = variant.prices
                .firstOrNull { it.currencyCode.equals("GBP", ignoreCase = true) && it.deletedAt == null }
                ?.amount?.multiply(BigDecimal(100))?.toInt() ?: 0

            val newPrice = update.amount
            val difference = newPrice - currentPrice
            val percentageChange = if (currentPrice > 0) {
                BigDecimal(difference).divide(BigDecimal(currentPrice), 4, RoundingMode.HALF_UP)
                    .multiply(BigDecimal(100))
            } else {
                BigDecimal.ZERO
            }

            SimulatedPriceChange(
                variantId = variant.id,
                productTitle = product.title,
                variantTitle = variant.title,
                currentPrice = currentPrice,
                newPrice = newPrice,
                priceDifference = difference,
                percentageChange = percentageChange
            )
        }

        return ResponseEntity.ok(previews)
    }

    // =========================================================================
    // Price Simulation
    // =========================================================================

    @Operation(summary = "Simulate price changes")
    @PostMapping("/simulate")
    fun simulatePriceChanges(@RequestBody request: PriceSimulationRequest): ResponseEntity<PriceSimulationResult> {
        logger.info { "POST /admin/pricing/simulate - ${request.variantIds.size} variants, ${request.adjustmentType}" }

        val simulations = request.variantIds.mapNotNull { variantId ->
            val variant = productVariantRepository.findByIdAndDeletedAtIsNull(variantId)
                ?: return@mapNotNull null
            val product = variant.product ?: return@mapNotNull null

            val currentPriceCents = variant.prices
                .firstOrNull { it.currencyCode.equals("GBP", ignoreCase = true) && it.deletedAt == null }
                ?.amount?.multiply(BigDecimal(100))?.toInt() ?: 0

            val newPriceCents = calculateNewPrice(
                currentPriceCents,
                request.adjustmentType,
                request.adjustmentValue,
                request.roundingStrategy
            )

            val difference = newPriceCents - currentPriceCents
            val percentageChange = if (currentPriceCents > 0) {
                BigDecimal(difference).divide(BigDecimal(currentPriceCents), 4, RoundingMode.HALF_UP)
                    .multiply(BigDecimal(100))
            } else {
                BigDecimal.ZERO
            }

            SimulatedPriceChange(
                variantId = variantId,
                productTitle = product.title,
                variantTitle = variant.title,
                currentPrice = currentPriceCents,
                newPrice = newPriceCents,
                priceDifference = difference,
                percentageChange = percentageChange
            )
        }

        val summary = SimulationSummary(
            totalVariants = simulations.size,
            totalCurrentValue = simulations.sumOf { it.currentPrice },
            totalNewValue = simulations.sumOf { it.newPrice },
            averageChange = if (simulations.isNotEmpty()) {
                simulations.map { it.percentageChange }.reduce { acc, d -> acc + d }
                    .divide(BigDecimal(simulations.size), 2, RoundingMode.HALF_UP)
            } else BigDecimal.ZERO,
            maxIncrease = simulations.maxByOrNull { it.priceDifference },
            maxDecrease = simulations.minByOrNull { it.priceDifference }
        )

        return ResponseEntity.ok(PriceSimulationResult(
            simulations = simulations,
            summary = summary
        ))
    }

    private fun calculateNewPrice(
        currentPriceCents: Int,
        adjustmentType: AdjustmentType,
        adjustmentValue: BigDecimal,
        roundingStrategy: RoundingStrategy?
    ): Int {
        var newPrice = when (adjustmentType) {
            AdjustmentType.PERCENTAGE -> {
                val multiplier = BigDecimal.ONE + adjustmentValue.divide(BigDecimal(100))
                BigDecimal(currentPriceCents).multiply(multiplier).toInt()
            }
            AdjustmentType.FIXED_AMOUNT -> {
                currentPriceCents + adjustmentValue.multiply(BigDecimal(100)).toInt()
            }
            AdjustmentType.SET_PRICE -> {
                adjustmentValue.multiply(BigDecimal(100)).toInt()
            }
        }

        // Apply rounding
        newPrice = when (roundingStrategy) {
            RoundingStrategy.ROUND_TO_99 -> {
                val pounds = newPrice / 100
                pounds * 100 + 99
            }
            RoundingStrategy.ROUND_TO_95 -> {
                val pounds = newPrice / 100
                pounds * 100 + 95
            }
            RoundingStrategy.ROUND_TO_NEAREST_POUND -> {
                ((newPrice + 50) / 100) * 100
            }
            RoundingStrategy.ROUND_TO_NEAREST_50P -> {
                ((newPrice + 25) / 50) * 50
            }
            else -> newPrice
        }

        return newPrice.coerceAtLeast(0)
    }

    // =========================================================================
    // Pricing Rules
    // =========================================================================

    @Operation(summary = "List pricing rules")
    @GetMapping("/rules")
    fun listRules(
        @RequestParam(required = false) status: String?,
        @RequestParam(required = false) type: String?
    ): ResponseEntity<PricingRulesResponse> {
        var rules = pricingRuleRepository.findByDeletedAtIsNull()

        if (!status.isNullOrBlank()) {
            val statusEnum = try { PricingRuleStatus.valueOf(status.uppercase()) } catch (e: Exception) { null }
            if (statusEnum != null) {
                rules = rules.filter { it.status == statusEnum }
            }
        }

        if (!type.isNullOrBlank()) {
            val typeEnum = try { PricingRuleType.valueOf(type.uppercase()) } catch (e: Exception) { null }
            if (typeEnum != null) {
                rules = rules.filter { it.type == typeEnum }
            }
        }

        rules = rules.sortedByDescending { it.priority }

        return ResponseEntity.ok(PricingRulesResponse(
            rules = rules.map { PricingRuleDto.from(it) },
            count = rules.size
        ))
    }

    @Operation(summary = "Get pricing rule by ID")
    @GetMapping("/rules/{id}")
    fun getRule(@PathVariable id: String): ResponseEntity<PricingRuleResponse> {
        val rule = pricingRuleRepository.findByIdAndDeletedAtIsNull(id)
            ?: return ResponseEntity.notFound().build()

        return ResponseEntity.ok(PricingRuleResponse(rule = PricingRuleDto.from(rule)))
    }

    @Operation(summary = "Create pricing rule")
    @PostMapping("/rules")
    @Transactional
    fun createRule(@RequestBody request: CreatePricingRuleRequest): ResponseEntity<Any> {
        logger.info { "POST /admin/pricing/rules - name=${request.name}" }

        if (pricingRuleRepository.existsByNameAndDeletedAtIsNull(request.name)) {
            return ResponseEntity.badRequest().body(mapOf(
                "message" to "A rule with this name already exists"
            ))
        }

        val ruleType = try {
            PricingRuleType.valueOf(request.type.uppercase())
        } catch (e: Exception) {
            return ResponseEntity.badRequest().body(mapOf(
                "message" to "Invalid rule type",
                "validTypes" to PricingRuleType.entries.map { it.name }
            ))
        }

        val rule = PricingRule().apply {
            name = request.name
            description = request.description
            type = ruleType
            config = request.config.toMutableMap()
            priority = request.priority
            startAt = request.startAt
            endAt = request.endAt
            status = if (request.activateImmediately) PricingRuleStatus.ACTIVE else PricingRuleStatus.INACTIVE

            request.targetType?.let {
                targetType = try { TargetType.valueOf(it.uppercase()) } catch (e: Exception) { null }
            }
            request.targetIds?.let { targetIds = it.toMutableList() }
        }

        val savedRule = pricingRuleRepository.save(rule)

        // Publish event
        publishRuleCreatedEvent(savedRule)

        return ResponseEntity.status(201).body(PricingRuleResponse(rule = PricingRuleDto.from(savedRule)))
    }

    @Operation(summary = "Update pricing rule")
    @PutMapping("/rules/{id}")
    @Transactional
    fun updateRule(
        @PathVariable id: String,
        @RequestBody request: UpdatePricingRuleRequest
    ): ResponseEntity<Any> {
        val rule = pricingRuleRepository.findByIdAndDeletedAtIsNull(id)
            ?: return ResponseEntity.notFound().build()

        request.name?.let {
            if (pricingRuleRepository.existsByNameAndIdNotAndDeletedAtIsNull(it, id)) {
                return ResponseEntity.badRequest().body(mapOf(
                    "message" to "A rule with this name already exists"
                ))
            }
            rule.name = it
        }
        request.description?.let { rule.description = it }
        request.config?.let { rule.config = it.toMutableMap() }
        request.priority?.let { rule.priority = it }
        request.startAt?.let { rule.startAt = it }
        request.endAt?.let { rule.endAt = it }
        request.targetType?.let {
            rule.targetType = try { TargetType.valueOf(it.uppercase()) } catch (e: Exception) { null }
        }
        request.targetIds?.let { rule.targetIds = it.toMutableList() }

        val savedRule = pricingRuleRepository.save(rule)
        return ResponseEntity.ok(PricingRuleResponse(rule = PricingRuleDto.from(savedRule)))
    }

    @Operation(summary = "Delete pricing rule")
    @DeleteMapping("/rules/{id}")
    @Transactional
    fun deleteRule(@PathVariable id: String): ResponseEntity<Any> {
        val rule = pricingRuleRepository.findByIdAndDeletedAtIsNull(id)
            ?: return ResponseEntity.notFound().build()

        rule.softDelete()
        pricingRuleRepository.save(rule)

        return ResponseEntity.ok(mapOf("message" to "Rule deleted", "id" to id))
    }

    @Operation(summary = "Activate pricing rule")
    @PostMapping("/rules/{id}/activate")
    @Transactional
    fun activateRule(@PathVariable id: String): ResponseEntity<Any> {
        val rule = pricingRuleRepository.findByIdAndDeletedAtIsNull(id)
            ?: return ResponseEntity.notFound().build()

        rule.status = PricingRuleStatus.ACTIVE
        val savedRule = pricingRuleRepository.save(rule)

        return ResponseEntity.ok(PricingRuleResponse(rule = PricingRuleDto.from(savedRule)))
    }

    @Operation(summary = "Deactivate pricing rule")
    @PostMapping("/rules/{id}/deactivate")
    @Transactional
    fun deactivateRule(@PathVariable id: String): ResponseEntity<Any> {
        val rule = pricingRuleRepository.findByIdAndDeletedAtIsNull(id)
            ?: return ResponseEntity.notFound().build()

        rule.status = PricingRuleStatus.INACTIVE
        val savedRule = pricingRuleRepository.save(rule)

        return ResponseEntity.ok(PricingRuleResponse(rule = PricingRuleDto.from(savedRule)))
    }

    // =========================================================================
    // Price History
    // =========================================================================

    @Operation(summary = "Get price change history")
    @GetMapping("/history")
    fun getPriceHistory(
        @RequestParam(defaultValue = "50") limit: Int,
        @RequestParam(defaultValue = "0") offset: Int,
        @RequestParam(required = false) variantId: String?,
        @RequestParam(required = false) changeType: String?
    ): ResponseEntity<PriceHistoryResponse> {
        val pageable = PageRequest.of(offset / limit.coerceAtLeast(1), limit.coerceAtMost(100))

        val changeTypeEnum = changeType?.let {
            try { PriceChangeType.valueOf(it.uppercase()) } catch (e: Exception) { null }
        }

        val changes = if (variantId != null) {
            priceChangeLogRepository.findByVariantIdOrderByChangedAtDesc(variantId, pageable)
        } else if (changeTypeEnum != null) {
            priceChangeLogRepository.findByChangeTypeOrderByChangedAtDesc(changeTypeEnum, pageable)
        } else {
            priceChangeLogRepository.findAllByOrderByChangedAtDesc(pageable)
        }

        return ResponseEntity.ok(PriceHistoryResponse(
            changes = changes.content.map { PriceChangeLogDto.from(it) },
            count = changes.totalElements,
            offset = offset,
            limit = limit
        ))
    }

    // =========================================================================
    // Activity Feed
    // =========================================================================

    @Operation(summary = "Get recent pricing activity")
    @GetMapping("/activity")
    fun getActivity(
        @RequestParam(defaultValue = "20") limit: Int
    ): ResponseEntity<PricingActivityResponse> {
        val since = Instant.now().minus(24, ChronoUnit.HOURS)
        val pageable = PageRequest.of(0, limit.coerceAtMost(50))

        val recentChanges = priceChangeLogRepository.findRecentActivity(since, pageable)

        val activities = recentChanges.content.map { log ->
            PricingActivityItem(
                id = log.id,
                type = "PRICE_CHANGE",
                message = buildActivityMessage(log),
                details = mapOf(
                    "variantId" to log.variantId,
                    "productTitle" to (log.productTitle ?: "Unknown"),
                    "previousPrice" to (log.previousAmount?.multiply(BigDecimal(100))?.toInt() ?: 0),
                    "newPrice" to log.newAmount.multiply(BigDecimal(100)).toInt()
                ),
                timestamp = log.changedAt.atOffset(ZoneOffset.UTC),
                actor = log.changedByName ?: log.changedBy
            )
        }

        return ResponseEntity.ok(PricingActivityResponse(
            activities = activities,
            count = recentChanges.totalElements
        ))
    }

    private fun buildActivityMessage(log: PriceChangeLog): String {
        val productName = log.productTitle ?: "Unknown product"
        val priceChange = log.getPriceDifference()
        val direction = if (priceChange > BigDecimal.ZERO) "increased" else "decreased"

        return when (log.changeType) {
            PriceChangeType.MANUAL -> "Price $direction for $productName"
            PriceChangeType.BULK_UPDATE -> "Bulk update: $productName"
            PriceChangeType.RULE_APPLIED -> "Rule '${log.ruleName}' applied to $productName"
            else -> "Price changed for $productName"
        }
    }

    // =========================================================================
    // WebSocket Publishing
    // =========================================================================

    private fun publishPriceChangeEvent(changeLog: PriceChangeLog, productTitle: String) {
        try {
            val event: Any = mapOf(
                "type" to "PRICE_CHANGED",
                "variantId" to changeLog.variantId,
                "productTitle" to productTitle,
                "previousPrice" to (changeLog.previousAmount?.multiply(BigDecimal(100))?.toInt()),
                "newPrice" to changeLog.newAmount.multiply(BigDecimal(100)).toInt(),
                "changedBy" to changeLog.changedBy,
                "timestamp" to Instant.now().toString()
            )
            messagingTemplate.convertAndSend("/topic/pricing", event)
        } catch (e: Exception) {
            logger.warn(e) { "Failed to publish price change event" }
        }
    }

    private fun publishBulkUpdateEvent(count: Int) {
        try {
            val event: Any = mapOf(
                "type" to "BULK_UPDATE",
                "count" to count,
                "timestamp" to Instant.now().toString()
            )
            messagingTemplate.convertAndSend("/topic/pricing", event)
        } catch (e: Exception) {
            logger.warn(e) { "Failed to publish bulk update event" }
        }
    }

    private fun publishRuleCreatedEvent(rule: PricingRule) {
        try {
            val event: Any = mapOf(
                "type" to "RULE_CREATED",
                "ruleId" to rule.id,
                "ruleName" to rule.name,
                "timestamp" to Instant.now().toString()
            )
            messagingTemplate.convertAndSend("/topic/pricing", event)
        } catch (e: Exception) {
            logger.warn(e) { "Failed to publish rule created event" }
        }
    }
}
