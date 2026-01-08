package com.vernont.application.customer

import com.vernont.domain.customer.UserFavorite
import com.vernont.repository.customer.UserFavoriteRepository
import com.vernont.repository.auth.UserRepository
import com.vernont.repository.product.ProductRepository
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.time.Instant

private val logger = KotlinLogging.logger {}

@Service
@Transactional
class FavoriteService(
    private val favoriteRepository: UserFavoriteRepository,
    private val userRepository: UserRepository,
    private val productRepository: ProductRepository
) {

    /**
     * Get all favorites for a user
     */
    @Transactional(readOnly = true)
    fun getUserFavorites(userId: String): List<FavoriteResponse> {
        logger.debug { "Getting favorites for user: $userId" }
        return favoriteRepository.findByUserIdAndDeletedAtIsNull(userId)
            .map { FavoriteResponse.from(it) }
    }

    /**
     * Add a product to favorites
     */
    fun addFavorite(userId: String, request: AddFavoriteRequest): FavoriteResponse {
        logger.info { "Adding product ${request.productId} to favorites for user: $userId" }

        // Check if already favorited
        val existing = favoriteRepository.findByUserIdAndProductIdAndDeletedAtIsNull(userId, request.productId)
        if (existing != null) {
            logger.debug { "Product already in favorites, returning existing" }
            return FavoriteResponse.from(existing)
        }

        val user = userRepository.findByIdWithRoles(userId)
            ?: throw FavoriteException("User not found: $userId")

        val product = productRepository.findByIdAndDeletedAtIsNull(request.productId)
            ?: throw FavoriteException("Product not found: ${request.productId}")

        val favorite = UserFavorite().apply {
            this.user = user
            this.product = product
            this.alertEnabled = request.alertEnabled ?: false
            this.priceThreshold = request.priceThreshold
        }

        val saved = favoriteRepository.save(favorite)
        logger.info { "Added favorite: ${saved.id}" }

        return FavoriteResponse.from(saved)
    }

    /**
     * Remove a product from favorites
     */
    fun removeFavorite(userId: String, productId: String) {
        logger.info { "Removing product $productId from favorites for user: $userId" }

        val favorite = favoriteRepository.findByUserIdAndProductIdAndDeletedAtIsNull(userId, productId)
            ?: throw FavoriteNotFoundException("Favorite not found for product: $productId")

        favorite.deletedAt = Instant.now()
        favoriteRepository.save(favorite)

        logger.info { "Removed favorite for product: $productId" }
    }

    /**
     * Check if a product is in favorites
     */
    @Transactional(readOnly = true)
    fun isFavorite(userId: String, productId: String): Boolean {
        return favoriteRepository.existsByUserIdAndProductIdAndDeletedAtIsNull(userId, productId)
    }

    /**
     * Update favorite settings (alerts, price threshold)
     */
    fun updateFavorite(userId: String, productId: String, request: UpdateFavoriteRequest): FavoriteResponse {
        logger.info { "Updating favorite settings for product $productId, user: $userId" }

        val favorite = favoriteRepository.findByUserIdAndProductIdAndDeletedAtIsNull(userId, productId)
            ?: throw FavoriteNotFoundException("Favorite not found for product: $productId")

        request.alertEnabled?.let { favorite.alertEnabled = it }
        request.priceThreshold?.let { favorite.priceThreshold = it }

        val updated = favoriteRepository.save(favorite)
        return FavoriteResponse.from(updated)
    }

    /**
     * Sync favorites from client (merge localStorage with backend)
     */
    fun syncFavorites(userId: String, productIds: List<String>): List<FavoriteResponse> {
        logger.info { "Syncing ${productIds.size} favorites for user: $userId" }

        val user = userRepository.findByIdWithRoles(userId)
            ?: throw FavoriteException("User not found: $userId")

        val existingFavorites = favoriteRepository.findByUserIdAndDeletedAtIsNull(userId)
            .associateBy { it.product.id }

        val newFavorites = mutableListOf<UserFavorite>()

        productIds.forEach { productId ->
            if (!existingFavorites.containsKey(productId)) {
                val product = productRepository.findByIdAndDeletedAtIsNull(productId)
                if (product != null) {
                    val favorite = UserFavorite().apply {
                        this.user = user
                        this.product = product
                    }
                    newFavorites.add(favorite)
                }
            }
        }

        if (newFavorites.isNotEmpty()) {
            favoriteRepository.saveAll(newFavorites)
            logger.info { "Added ${newFavorites.size} new favorites from sync" }
        }

        return favoriteRepository.findByUserIdAndDeletedAtIsNull(userId)
            .map { FavoriteResponse.from(it) }
    }
}

// DTOs

data class AddFavoriteRequest(
    val productId: String,
    val alertEnabled: Boolean? = false,
    val priceThreshold: BigDecimal? = null
)

data class UpdateFavoriteRequest(
    val alertEnabled: Boolean? = null,
    val priceThreshold: BigDecimal? = null
)

data class SyncFavoritesRequest(
    val productIds: List<String>
)

data class FavoriteResponse(
    val id: String,
    val productId: String,
    val productHandle: String?,
    val productTitle: String?,
    val productThumbnail: String?,
    val alertEnabled: Boolean,
    val priceThreshold: BigDecimal?,
    val createdAt: Instant
) {
    companion object {
        fun from(favorite: UserFavorite) = FavoriteResponse(
            id = favorite.id,
            productId = favorite.product.id,
            productHandle = favorite.product.handle,
            productTitle = favorite.product.title,
            productThumbnail = favorite.product.thumbnail,
            alertEnabled = favorite.alertEnabled,
            priceThreshold = favorite.priceThreshold,
            createdAt = favorite.createdAt
        )
    }
}

data class FavoriteCheckResponse(
    val productId: String,
    val isFavorite: Boolean
)

// Exceptions

class FavoriteException(message: String) : RuntimeException(message)
class FavoriteNotFoundException(message: String) : RuntimeException(message)
