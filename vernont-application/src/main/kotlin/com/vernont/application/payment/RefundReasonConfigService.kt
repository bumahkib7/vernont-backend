package com.vernont.application.payment

import com.vernont.domain.payment.RefundReasonConfig
import com.vernont.repository.payment.RefundReasonConfigRepository
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

private val logger = KotlinLogging.logger {}

/**
 * Service for managing refund reason configurations.
 * Handles CRUD operations and business logic for refund reasons.
 */
@Service
@Transactional
class RefundReasonConfigService(
    private val refundReasonConfigRepository: RefundReasonConfigRepository
) {

    /**
     * List all refund reasons with optional filtering.
     */
    @Transactional(readOnly = true)
    fun list(
        active: Boolean? = null,
        searchQuery: String? = null
    ): List<RefundReasonConfig> {
        logger.debug { "Listing refund reasons - active=$active, q=$searchQuery" }

        var reasons = refundReasonConfigRepository.findByDeletedAtIsNullOrderByDisplayOrderAsc()

        // Filter by active status
        if (active != null) {
            reasons = reasons.filter { it.isActive == active }
        }

        // Filter by search query
        if (!searchQuery.isNullOrBlank()) {
            val searchTerm = searchQuery.lowercase()
            reasons = reasons.filter { reason ->
                reason.value.lowercase().contains(searchTerm) ||
                reason.label.lowercase().contains(searchTerm) ||
                reason.description?.lowercase()?.contains(searchTerm) == true
            }
        }

        return reasons
    }

    /**
     * Get a single refund reason by ID.
     * @throws RefundReasonNotFoundException if not found
     */
    @Transactional(readOnly = true)
    fun getById(id: String): RefundReasonConfig {
        logger.debug { "Getting refund reason by ID: $id" }
        return refundReasonConfigRepository.findByIdAndDeletedAtIsNull(id)
            ?: throw RefundReasonNotFoundException("Refund reason with ID $id not found")
    }

    /**
     * Get active refund reasons (for storefront/customer use).
     */
    @Transactional(readOnly = true)
    fun getActiveReasons(): List<RefundReasonConfig> {
        return refundReasonConfigRepository.findByIsActiveTrueAndDeletedAtIsNullOrderByDisplayOrderAsc()
    }

    /**
     * Create a new refund reason.
     * @throws RefundReasonValueExistsException if value already exists
     * @throws IllegalArgumentException if required fields are invalid
     */
    fun create(input: CreateRefundReasonInput): RefundReasonConfig {
        logger.info { "Creating refund reason: value=${input.value}, label=${input.label}" }

        // Validate required fields
        require(input.value.isNotBlank()) { "Value is required" }
        require(input.label.isNotBlank()) { "Label is required" }

        // Normalize value to snake_case
        val normalizedValue = normalizeValue(input.value)

        // Check for duplicate value
        if (refundReasonConfigRepository.existsByValueAndDeletedAtIsNull(normalizedValue)) {
            throw RefundReasonValueExistsException("A refund reason with value '$normalizedValue' already exists")
        }

        val reason = RefundReasonConfig().apply {
            value = normalizedValue
            label = input.label
            description = input.description
            displayOrder = input.displayOrder
            isActive = input.isActive
            requiresNote = input.requiresNote
        }

        val saved = refundReasonConfigRepository.save(reason)
        logger.info { "Created refund reason: ${saved.id}" }
        return saved
    }

    /**
     * Update an existing refund reason.
     * @throws RefundReasonNotFoundException if not found
     * @throws RefundReasonValueExistsException if new value conflicts with existing
     */
    fun update(id: String, input: UpdateRefundReasonInput): RefundReasonConfig {
        logger.info { "Updating refund reason: $id" }

        val reason = getById(id)

        // Update value if provided and changed
        input.value?.let { newValue ->
            if (newValue != reason.value) {
                val normalizedValue = normalizeValue(newValue)
                if (refundReasonConfigRepository.existsByValueAndIdNotAndDeletedAtIsNull(normalizedValue, id)) {
                    throw RefundReasonValueExistsException("A refund reason with value '$normalizedValue' already exists")
                }
                reason.value = normalizedValue
            }
        }

        // Update other fields if provided
        input.label?.let { reason.label = it }
        input.description?.let { reason.description = it }
        input.displayOrder?.let { reason.displayOrder = it }
        input.isActive?.let { reason.isActive = it }
        input.requiresNote?.let { reason.requiresNote = it }

        val saved = refundReasonConfigRepository.save(reason)
        logger.info { "Updated refund reason: $id" }
        return saved
    }

    /**
     * Soft delete a refund reason.
     * @throws RefundReasonNotFoundException if not found
     */
    fun delete(id: String) {
        logger.info { "Deleting refund reason: $id" }

        val reason = getById(id)
        reason.softDelete()
        refundReasonConfigRepository.save(reason)

        logger.info { "Soft deleted refund reason: $id" }
    }

    /**
     * Reorder refund reasons by providing ordered list of IDs.
     */
    fun reorder(ids: List<String>) {
        logger.info { "Reordering ${ids.size} refund reasons" }

        ids.forEachIndexed { index, id ->
            refundReasonConfigRepository.findByIdAndDeletedAtIsNull(id)?.let { reason ->
                reason.displayOrder = index
                refundReasonConfigRepository.save(reason)
            }
        }

        logger.info { "Reordered ${ids.size} refund reasons" }
    }

    /**
     * Seed default refund reasons.
     * @throws IllegalStateException if reasons already exist
     */
    fun seedDefaults(): List<RefundReasonConfig> {
        logger.info { "Seeding default refund reasons" }

        val existing = refundReasonConfigRepository.findByDeletedAtIsNull()
        if (existing.isNotEmpty()) {
            throw IllegalStateException("Refund reasons already exist. Delete all before seeding.")
        }

        val defaults = RefundReasonConfig.createDefaults()
        val saved = refundReasonConfigRepository.saveAll(defaults)

        logger.info { "Seeded ${saved.size} default refund reasons" }
        return saved
    }

    /**
     * Normalize a value string to snake_case.
     */
    private fun normalizeValue(value: String): String {
        return value.lowercase()
            .replace(Regex("[^a-z0-9]+"), "_")
            .trim('_')
    }
}

// ============================================================================
// Input DTOs
// ============================================================================

data class CreateRefundReasonInput(
    val value: String,
    val label: String,
    val description: String? = null,
    val displayOrder: Int = 0,
    val isActive: Boolean = true,
    val requiresNote: Boolean = false
)

data class UpdateRefundReasonInput(
    val value: String? = null,
    val label: String? = null,
    val description: String? = null,
    val displayOrder: Int? = null,
    val isActive: Boolean? = null,
    val requiresNote: Boolean? = null
)

// ============================================================================
// Exceptions
// ============================================================================

class RefundReasonNotFoundException(message: String) : RuntimeException(message)
class RefundReasonValueExistsException(message: String) : RuntimeException(message)
