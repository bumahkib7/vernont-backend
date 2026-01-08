package com.vernont.application.store

import com.vernont.domain.store.Store
import com.vernont.repository.store.StoreRepository
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

private val logger = KotlinLogging.logger {}

@Service
@Transactional
class StoreService(
    private val storeRepository: StoreRepository,
    private val storeSettingsService: StoreSettingsService
) {

    /**
     * List all active stores
     */
    @Transactional(readOnly = true)
    fun listStores(
        limit: Int = 20,
        offset: Int = 0,
        search: String? = null
    ): StoreListResult {
        logger.debug { "Listing stores - limit=$limit, offset=$offset, search=$search" }

        var stores = if (!search.isNullOrBlank()) {
            storeRepository.searchByName(search)
        } else {
            storeRepository.findByDeletedAtIsNull()
        }

        val total = stores.size
        stores = stores.drop(offset).take(limit)

        return StoreListResult(
            stores = stores,
            total = total,
            limit = limit,
            offset = offset
        )
    }

    /**
     * Get store by ID
     */
    @Transactional(readOnly = true)
    fun getStore(storeId: String): Store {
        logger.debug { "Getting store: $storeId" }
        return storeRepository.findByIdAndDeletedAtIsNull(storeId)
            ?: throw StoreNotFoundException("Store not found: $storeId")
    }

    /**
     * Create a new store
     */
    fun createStore(request: CreateStoreRequest): Store {
        logger.info { "Creating store: ${request.name}" }

        if (storeRepository.existsByName(request.name)) {
            throw IllegalArgumentException("Store with name '${request.name}' already exists")
        }

        val store = Store().apply {
            name = request.name
            defaultCurrencyCode = request.defaultCurrencyCode.uppercase()
            swapLinkTemplate = request.swapLinkTemplate
            paymentLinkTemplate = request.paymentLinkTemplate
            inviteLinkTemplate = request.inviteLinkTemplate
        }

        val savedStore = storeRepository.save(store)

        // Auto-create settings for the new store
        storeSettingsService.initializeSettings(savedStore.id)

        return savedStore
    }

    /**
     * Update a store
     */
    fun updateStore(storeId: String, request: UpdateStoreRequest): Store {
        logger.info { "Updating store: $storeId" }

        val store = getStore(storeId)

        request.name?.let {
            if (storeRepository.existsByNameAndIdNot(it, storeId)) {
                throw IllegalArgumentException("Store with name '$it' already exists")
            }
            store.name = it
        }
        request.defaultCurrencyCode?.let { store.defaultCurrencyCode = it.uppercase() }
        request.swapLinkTemplate?.let { store.swapLinkTemplate = it }
        request.paymentLinkTemplate?.let { store.paymentLinkTemplate = it }
        request.inviteLinkTemplate?.let { store.inviteLinkTemplate = it }
        request.defaultSalesChannelId?.let { store.defaultSalesChannelId = it }
        request.defaultRegionId?.let { store.defaultRegionId = it }
        request.defaultLocationId?.let { store.defaultLocationId = it }

        return storeRepository.save(store)
    }

    /**
     * Soft delete a store
     */
    fun deleteStore(storeId: String) {
        logger.info { "Deleting store: $storeId" }

        val store = getStore(storeId)
        store.deletedAt = Instant.now()
        storeRepository.save(store)
    }

    /**
     * Get total count of active stores
     */
    @Transactional(readOnly = true)
    fun countStores(): Long {
        return storeRepository.countActiveStores()
    }
}

// ============================================================================
// DTOs
// ============================================================================

data class StoreListResult(
    val stores: List<Store>,
    val total: Int,
    val limit: Int,
    val offset: Int
)

data class CreateStoreRequest(
    val name: String,
    val defaultCurrencyCode: String = "GBP",
    val swapLinkTemplate: String? = null,
    val paymentLinkTemplate: String? = null,
    val inviteLinkTemplate: String? = null
)

data class UpdateStoreRequest(
    val name: String? = null,
    val defaultCurrencyCode: String? = null,
    val swapLinkTemplate: String? = null,
    val paymentLinkTemplate: String? = null,
    val inviteLinkTemplate: String? = null,
    val defaultSalesChannelId: String? = null,
    val defaultRegionId: String? = null,
    val defaultLocationId: String? = null
)

// ============================================================================
// Exceptions
// ============================================================================

class StoreNotFoundException(message: String) : RuntimeException(message)
