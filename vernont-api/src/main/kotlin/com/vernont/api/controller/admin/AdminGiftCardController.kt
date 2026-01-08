package com.vernont.api.controller.admin

import com.vernont.api.dto.admin.*
import com.vernont.domain.giftcard.GiftCard
import com.vernont.domain.giftcard.GiftCardStatus
import com.vernont.repository.giftcard.GiftCardRepository
import io.github.oshai.kotlinlogging.KotlinLogging
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.data.domain.PageRequest
import org.springframework.http.ResponseEntity
import org.springframework.messaging.simp.SimpMessagingTemplate
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.bind.annotation.*
import java.time.Instant
import java.time.temporal.ChronoUnit

private val logger = KotlinLogging.logger {}

@RestController
@RequestMapping("/admin/gift-cards")
@Tag(name = "Admin Gift Cards", description = "Gift card management endpoints")
class AdminGiftCardController(
    private val giftCardRepository: GiftCardRepository,
    private val messagingTemplate: SimpMessagingTemplate
) {

    // =========================================================================
    // List & Get
    // =========================================================================

    @Operation(summary = "List all gift cards")
    @GetMapping
    fun listGiftCards(
        @RequestParam(defaultValue = "50") limit: Int,
        @RequestParam(defaultValue = "0") offset: Int,
        @RequestParam(required = false) q: String?,
        @RequestParam(required = false) status: String?
    ): ResponseEntity<GiftCardsListResponse> {
        logger.info { "GET /admin/gift-cards - limit=$limit, offset=$offset, q=$q, status=$status" }

        val statusEnum = if (!status.isNullOrBlank()) {
            try { GiftCardStatus.valueOf(status.uppercase()) } catch (e: Exception) { null }
        } else null

        val pageable = PageRequest.of(offset / limit.coerceAtLeast(1), limit.coerceAtMost(100))

        val giftCardsPage = giftCardRepository.findByFilters(
            status = statusEnum,
            customerId = null,
            code = q,
            pageable = pageable
        )

        val items = giftCardsPage.content
            .filter { it.deletedAt == null }
            .map { GiftCardListItem.from(it) }

        return ResponseEntity.ok(GiftCardsListResponse(
            items = items,
            count = giftCardsPage.totalElements,
            offset = offset,
            limit = limit
        ))
    }

    @Operation(summary = "Get gift card details by ID")
    @GetMapping("/{id}")
    fun getGiftCard(@PathVariable id: String): ResponseEntity<GiftCardResponse> {
        logger.info { "GET /admin/gift-cards/$id" }

        val giftCard = giftCardRepository.findByIdAndDeletedAtIsNull(id)
            ?: return ResponseEntity.notFound().build()

        return ResponseEntity.ok(GiftCardResponse(
            giftCard = GiftCardDetail.from(giftCard)
        ))
    }

    @Operation(summary = "Look up gift card by code")
    @GetMapping("/lookup")
    fun lookupByCode(@RequestParam code: String): ResponseEntity<GiftCardResponse> {
        logger.info { "GET /admin/gift-cards/lookup - code=$code" }

        val giftCard = giftCardRepository.findByCodeIgnoreCase(code)
            ?: return ResponseEntity.notFound().build()

        if (giftCard.deletedAt != null) {
            return ResponseEntity.notFound().build()
        }

        return ResponseEntity.ok(GiftCardResponse(
            giftCard = GiftCardDetail.from(giftCard)
        ))
    }

    // =========================================================================
    // Create & Update
    // =========================================================================

    @Operation(summary = "Create a new gift card")
    @PostMapping
    @Transactional
    fun createGiftCard(@RequestBody request: CreateGiftCardRequest): ResponseEntity<Any> {
        logger.info { "POST /admin/gift-cards - amount=${request.amount}" }

        if (request.amount <= 0) {
            return ResponseEntity.badRequest().body(mapOf(
                "message" to "Amount must be positive"
            ))
        }

        val expiresAt = request.expiresInDays?.let {
            Instant.now().plus(it.toLong(), ChronoUnit.DAYS)
        }

        val giftCard = GiftCard.create(
            amount = request.amount,
            currencyCode = request.currencyCode,
            issuedToCustomerId = null,
            issuedByUserId = "admin", // TODO: Get from security context
            message = request.message,
            recipientEmail = request.recipientEmail,
            recipientName = request.recipientName,
            expiresAt = expiresAt
        )

        val savedGiftCard = giftCardRepository.save(giftCard)

        // Publish WebSocket event
        publishGiftCardEvent("GIFT_CARD_CREATED", savedGiftCard)

        return ResponseEntity.status(201).body(GiftCardResponse(
            giftCard = GiftCardDetail.from(savedGiftCard)
        ))
    }

    @Operation(summary = "Update a gift card")
    @PutMapping("/{id}")
    @Transactional
    fun updateGiftCard(
        @PathVariable id: String,
        @RequestBody request: UpdateGiftCardRequest
    ): ResponseEntity<Any> {
        logger.info { "PUT /admin/gift-cards/$id" }

        val giftCard = giftCardRepository.findByIdAndDeletedAtIsNull(id)
            ?: return ResponseEntity.notFound().build()

        request.recipientName?.let { giftCard.recipientName = it }
        request.recipientEmail?.let { giftCard.recipientEmail = it }
        request.message?.let { giftCard.message = it }
        request.expiresAt?.let { giftCard.expiresAt = it }

        val savedGiftCard = giftCardRepository.save(giftCard)

        // Publish WebSocket event
        publishGiftCardEvent("GIFT_CARD_UPDATED", savedGiftCard)

        return ResponseEntity.ok(GiftCardResponse(
            giftCard = GiftCardDetail.from(savedGiftCard)
        ))
    }

    @Operation(summary = "Adjust gift card balance")
    @PostMapping("/{id}/adjust-balance")
    @Transactional
    fun adjustBalance(
        @PathVariable id: String,
        @RequestBody request: AdjustBalanceRequest
    ): ResponseEntity<Any> {
        logger.info { "POST /admin/gift-cards/$id/adjust-balance - amount=${request.amount}" }

        val giftCard = giftCardRepository.findByIdAndDeletedAtIsNull(id)
            ?: return ResponseEntity.notFound().build()

        if (request.amount > 0) {
            giftCard.addBalance(request.amount)
        } else if (request.amount < 0) {
            val deductAmount = -request.amount
            if (deductAmount > giftCard.remainingAmount) {
                return ResponseEntity.badRequest().body(mapOf(
                    "message" to "Cannot deduct more than the remaining balance"
                ))
            }
            giftCard.remainingAmount -= deductAmount
            if (giftCard.remainingAmount == 0) {
                giftCard.status = GiftCardStatus.FULLY_REDEEMED
                giftCard.fullyRedeemedAt = Instant.now()
            }
        }

        val savedGiftCard = giftCardRepository.save(giftCard)

        // Publish WebSocket event
        publishGiftCardEvent("GIFT_CARD_BALANCE_ADJUSTED", savedGiftCard)

        return ResponseEntity.ok(GiftCardResponse(
            giftCard = GiftCardDetail.from(savedGiftCard)
        ))
    }

    // =========================================================================
    // Status Changes
    // =========================================================================

    @Operation(summary = "Disable a gift card")
    @PostMapping("/{id}/disable")
    @Transactional
    fun disableGiftCard(@PathVariable id: String): ResponseEntity<Any> {
        logger.info { "POST /admin/gift-cards/$id/disable" }

        val giftCard = giftCardRepository.findByIdAndDeletedAtIsNull(id)
            ?: return ResponseEntity.notFound().build()

        giftCard.disable()
        val savedGiftCard = giftCardRepository.save(giftCard)

        // Publish WebSocket event
        publishGiftCardEvent("GIFT_CARD_DISABLED", savedGiftCard)

        return ResponseEntity.ok(GiftCardResponse(
            giftCard = GiftCardDetail.from(savedGiftCard)
        ))
    }

    @Operation(summary = "Enable a disabled gift card")
    @PostMapping("/{id}/enable")
    @Transactional
    fun enableGiftCard(@PathVariable id: String): ResponseEntity<Any> {
        logger.info { "POST /admin/gift-cards/$id/enable" }

        val giftCard = giftCardRepository.findByIdAndDeletedAtIsNull(id)
            ?: return ResponseEntity.notFound().build()

        giftCard.enable()
        val savedGiftCard = giftCardRepository.save(giftCard)

        // Publish WebSocket event
        publishGiftCardEvent("GIFT_CARD_ENABLED", savedGiftCard)

        return ResponseEntity.ok(GiftCardResponse(
            giftCard = GiftCardDetail.from(savedGiftCard)
        ))
    }

    @Operation(summary = "Delete a gift card (soft delete)")
    @DeleteMapping("/{id}")
    @Transactional
    fun deleteGiftCard(@PathVariable id: String): ResponseEntity<Any> {
        logger.info { "DELETE /admin/gift-cards/$id" }

        val giftCard = giftCardRepository.findByIdAndDeletedAtIsNull(id)
            ?: return ResponseEntity.notFound().build()

        giftCard.softDelete()
        giftCardRepository.save(giftCard)

        // Publish WebSocket event
        publishGiftCardEvent("GIFT_CARD_DELETED", giftCard)

        return ResponseEntity.ok(mapOf(
            "message" to "Gift card deleted",
            "id" to id
        ))
    }

    // =========================================================================
    // Bulk Operations
    // =========================================================================

    @Operation(summary = "Perform bulk operations on gift cards")
    @PostMapping("/bulk")
    @Transactional
    fun bulkAction(@RequestBody request: BulkGiftCardRequest): ResponseEntity<BulkGiftCardResult> {
        logger.info { "POST /admin/gift-cards/bulk - action=${request.action}, ids=${request.ids.size}" }

        val errors = mutableListOf<BulkGiftCardError>()
        var successCount = 0

        for (giftCardId in request.ids) {
            try {
                val giftCard = giftCardRepository.findByIdAndDeletedAtIsNull(giftCardId)
                    ?: throw IllegalArgumentException("Gift card not found")

                when (request.action.uppercase()) {
                    "DISABLE" -> giftCard.disable()
                    "ENABLE" -> giftCard.enable()
                    "DELETE" -> giftCard.softDelete()
                    else -> throw IllegalArgumentException("Invalid action: ${request.action}")
                }

                giftCardRepository.save(giftCard)
                successCount++

            } catch (e: Exception) {
                logger.error(e) { "Failed to perform bulk action on gift card $giftCardId" }
                errors.add(BulkGiftCardError(giftCardId, e.message ?: "Unknown error"))
            }
        }

        return ResponseEntity.ok(BulkGiftCardResult(
            successCount = successCount,
            failureCount = errors.size,
            errors = errors
        ))
    }

    // =========================================================================
    // Stats & Analytics
    // =========================================================================

    @Operation(summary = "Get gift card statistics")
    @GetMapping("/stats")
    fun getStats(): ResponseEntity<GiftCardStatsResponse> {
        logger.info { "GET /admin/gift-cards/stats" }

        val now = Instant.now()
        val weekFromNow = now.plus(7, ChronoUnit.DAYS)
        val weekAgo = now.minus(7, ChronoUnit.DAYS)

        val activeCount = giftCardRepository.countByStatus(GiftCardStatus.ACTIVE)
        val fullyRedeemedCount = giftCardRepository.countByStatus(GiftCardStatus.FULLY_REDEEMED)
        val expiredCount = giftCardRepository.countByStatus(GiftCardStatus.EXPIRED)
        val disabledCount = giftCardRepository.countByStatus(GiftCardStatus.DISABLED)
        val totalCount = activeCount + fullyRedeemedCount + expiredCount + disabledCount

        val totalRemainingValue = giftCardRepository.getTotalUnredeemedValue()
        val totalIssuedValue = giftCardRepository.getTotalIssuedValueBetween(Instant.EPOCH, now)
        val totalRedeemedValue = totalIssuedValue - totalRemainingValue

        val expiringThisWeek = giftCardRepository.findExpiringBetween(now, weekFromNow).size
        val issuedThisWeek = giftCardRepository.getTotalIssuedValueBetween(weekAgo, now).let { value ->
            // Count cards issued this week (simplified - counts value, not cards)
            giftCardRepository.findByFilters(null, null, null, PageRequest.of(0, 1000))
                .content
                .count { it.createdAt?.isAfter(weekAgo) == true }
        }

        return ResponseEntity.ok(GiftCardStatsResponse(
            totalGiftCards = totalCount,
            activeGiftCards = activeCount,
            fullyRedeemedGiftCards = fullyRedeemedCount,
            expiredGiftCards = expiredCount,
            disabledGiftCards = disabledCount,
            totalIssuedValue = totalIssuedValue,
            totalRemainingValue = totalRemainingValue,
            totalRedeemedValue = totalRedeemedValue,
            formattedIssuedValue = formatCurrency(totalIssuedValue, "GBP"),
            formattedRemainingValue = formatCurrency(totalRemainingValue, "GBP"),
            formattedRedeemedValue = formatCurrency(totalRedeemedValue, "GBP"),
            expiringThisWeek = expiringThisWeek,
            issuedThisWeek = issuedThisWeek
        ))
    }

    // =========================================================================
    // Helper Methods
    // =========================================================================

    private fun publishGiftCardEvent(eventType: String, giftCard: GiftCard) {
        try {
            val event: Any = mapOf(
                "type" to eventType,
                "giftCardId" to giftCard.id,
                "giftCardCode" to giftCard.code,
                "message" to "$eventType: ${giftCard.code}",
                "timestamp" to Instant.now().toString()
            )
            messagingTemplate.convertAndSend("/topic/gift-cards", event)
        } catch (e: Exception) {
            logger.warn(e) { "Failed to publish gift card event" }
        }
    }

    private fun formatCurrency(amountInMinorUnits: Long, currencyCode: String): String {
        val amount = amountInMinorUnits / 100.0
        return when (currencyCode) {
            "GBP" -> "£%.2f".format(amount)
            "USD" -> "$%.2f".format(amount)
            "EUR" -> "€%.2f".format(amount)
            else -> "%.2f $currencyCode".format(amount)
        }
    }
}
