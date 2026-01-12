package com.vernont.application.returns

import com.vernont.domain.returns.ReturnReasonConfig
import com.vernont.repository.returns.ReturnReasonConfigRepository
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

private val logger = KotlinLogging.logger {}

/**
 * Service for managing return reason configurations.
 * Handles CRUD operations and business logic for return reasons.
 */
@Service
@Transactional
class ReturnReasonConfigService(
    private val returnReasonConfigRepository: ReturnReasonConfigRepository
) {

    /**
     * List all return reasons with optional filtering.
     */
    @Transactional(readOnly = true)
    fun list(
        active: Boolean? = null,
        searchQuery: String? = null
    ): List<ReturnReasonConfig> {
        logger.debug { "Listing return reasons - active=$active, q=$searchQuery" }

        var reasons = returnReasonConfigRepository.findByDeletedAtIsNullOrderByDisplayOrderAsc()

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
     * Get a single return reason by ID.
     * @throws ReturnReasonNotFoundException if not found
     */
    @Transactional(readOnly = true)
    fun getById(id: String): ReturnReasonConfig {
        logger.debug { "Getting return reason by ID: $id" }
        return returnReasonConfigRepository.findByIdAndDeletedAtIsNull(id)
            ?: throw ReturnReasonNotFoundException("Return reason with ID $id not found")
    }

    /**
     * Get active return reasons (for storefront/customer use).
     */
    @Transactional(readOnly = true)
    fun getActiveReasons(): List<ReturnReasonConfig> {
        return returnReasonConfigRepository.findByIsActiveTrueAndDeletedAtIsNullOrderByDisplayOrderAsc()
    }

    /**
     * Create a new return reason.
     * @throws ReturnReasonValueExistsException if value already exists
     * @throws IllegalArgumentException if required fields are invalid
     */
    fun create(input: CreateReturnReasonInput): ReturnReasonConfig {
        logger.info { "Creating return reason: value=${input.value}, label=${input.label}" }

        // Validate required fields
        require(input.value.isNotBlank()) { "Value is required" }
        require(input.label.isNotBlank()) { "Label is required" }

        // Normalize value to snake_case
        val normalizedValue = normalizeValue(input.value)

        // Check for duplicate value
        if (returnReasonConfigRepository.existsByValueAndDeletedAtIsNull(normalizedValue)) {
            throw ReturnReasonValueExistsException("A return reason with value '$normalizedValue' already exists")
        }

        val reason = ReturnReasonConfig().apply {
            value = normalizedValue
            label = input.label
            description = input.description
            displayOrder = input.displayOrder
            isActive = input.isActive
            requiresNote = input.requiresNote
        }

        val saved = returnReasonConfigRepository.save(reason)
        logger.info { "Created return reason: ${saved.id}" }
        return saved
    }

    /**
     * Update an existing return reason.
     * @throws ReturnReasonNotFoundException if not found
     * @throws ReturnReasonValueExistsException if new value conflicts with existing
     */
    fun update(id: String, input: UpdateReturnReasonInput): ReturnReasonConfig {
        logger.info { "Updating return reason: $id" }

        val reason = getById(id)

        // Update value if provided and changed
        input.value?.let { newValue ->
            if (newValue != reason.value) {
                val normalizedValue = normalizeValue(newValue)
                if (returnReasonConfigRepository.existsByValueAndIdNotAndDeletedAtIsNull(normalizedValue, id)) {
                    throw ReturnReasonValueExistsException("A return reason with value '$normalizedValue' already exists")
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

        val saved = returnReasonConfigRepository.save(reason)
        logger.info { "Updated return reason: $id" }
        return saved
    }

    /**
     * Soft delete a return reason.
     * @throws ReturnReasonNotFoundException if not found
     */
    fun delete(id: String) {
        logger.info { "Deleting return reason: $id" }

        val reason = getById(id)
        reason.softDelete()
        returnReasonConfigRepository.save(reason)

        logger.info { "Soft deleted return reason: $id" }
    }

    /**
     * Reorder return reasons by providing ordered list of IDs.
     */
    fun reorder(ids: List<String>) {
        logger.info { "Reordering ${ids.size} return reasons" }

        ids.forEachIndexed { index, id ->
            returnReasonConfigRepository.findByIdAndDeletedAtIsNull(id)?.let { reason ->
                reason.displayOrder = index
                returnReasonConfigRepository.save(reason)
            }
        }

        logger.info { "Reordered ${ids.size} return reasons" }
    }

    /**
     * Seed default return reasons.
     * @throws IllegalStateException if reasons already exist
     */
    fun seedDefaults(): List<ReturnReasonConfig> {
        logger.info { "Seeding default return reasons" }

        val existing = returnReasonConfigRepository.findByDeletedAtIsNull()
        if (existing.isNotEmpty()) {
            throw IllegalStateException("Return reasons already exist. Delete all before seeding.")
        }

        val defaults = ReturnReasonConfig.createDefaults()
        val saved = returnReasonConfigRepository.saveAll(defaults)

        logger.info { "Seeded ${saved.size} default return reasons" }
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

data class CreateReturnReasonInput(
    val value: String,
    val label: String,
    val description: String? = null,
    val displayOrder: Int = 0,
    val isActive: Boolean = true,
    val requiresNote: Boolean = false
)

data class UpdateReturnReasonInput(
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

class ReturnReasonNotFoundException(message: String) : RuntimeException(message)
class ReturnReasonValueExistsException(message: String) : RuntimeException(message)
