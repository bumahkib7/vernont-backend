package com.vernont.repository.store

import com.vernont.domain.store.StoreSettings
import org.springframework.data.jpa.repository.EntityGraph
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository

@Repository
interface StoreSettingsRepository : JpaRepository<StoreSettings, String> {

    /**
     * Find settings by store ID (soft-delete aware)
     */
    fun findByStoreIdAndDeletedAtIsNull(storeId: String): StoreSettings?

    /**
     * Find settings by store ID (including deleted)
     */
    fun findByStoreId(storeId: String): StoreSettings?

    /**
     * Find settings with store eagerly loaded
     */
    @EntityGraph(value = "StoreSettings.full")
    @Query("SELECT ss FROM StoreSettings ss WHERE ss.store.id = :storeId AND ss.deletedAt IS NULL AND ss.store.deletedAt IS NULL")
    fun findByStoreIdWithStore(@Param("storeId") storeId: String): StoreSettings?

    /**
     * Check if settings exist for a store
     */
    fun existsByStoreIdAndDeletedAtIsNull(storeId: String): Boolean

    /**
     * Check if settings exist for a store (including deleted)
     */
    fun existsByStoreId(storeId: String): Boolean

    /**
     * Find all stores with guest checkout enabled
     */
    @Query("SELECT ss FROM StoreSettings ss WHERE ss.guestCheckoutEnabled = true AND ss.deletedAt IS NULL")
    fun findStoresWithGuestCheckoutEnabled(): List<StoreSettings>

    /**
     * Find all stores with gift cards enabled
     */
    @Query("SELECT ss FROM StoreSettings ss WHERE ss.giftCardsEnabled = true AND ss.deletedAt IS NULL")
    fun findStoresWithGiftCardsEnabled(): List<StoreSettings>

    /**
     * Find all stores with reviews enabled
     */
    @Query("SELECT ss FROM StoreSettings ss WHERE ss.reviewsEnabled = true AND ss.deletedAt IS NULL")
    fun findStoresWithReviewsEnabled(): List<StoreSettings>

    /**
     * Find all stores with wishlist enabled
     */
    @Query("SELECT ss FROM StoreSettings ss WHERE ss.wishlistEnabled = true AND ss.deletedAt IS NULL")
    fun findStoresWithWishlistEnabled(): List<StoreSettings>

    /**
     * Find all active store settings
     */
    fun findAllByDeletedAtIsNull(): List<StoreSettings>
}
