package com.vernont.api.controller.store

import com.vernont.domain.auth.UserContext
import com.vernont.domain.auth.getCurrentUserContext
import com.vernont.application.customer.*
import io.github.oshai.kotlinlogging.KotlinLogging
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.*

private val logger = KotlinLogging.logger {}

@RestController
@RequestMapping("/store/favorites")
@Tag(name = "Store Favorites", description = "Customer favorites/wishlist management")
class FavoriteController(
    private val favoriteService: FavoriteService
) {

    /**
     * Gets favorites for user; returns favorites list
     */
    @Operation(summary = "Get all favorites for logged-in customer")
    @GetMapping
    fun getFavorites(
        @AuthenticationPrincipal userContext: UserContext?
    ): ResponseEntity<FavoritesListResponse> {
        val context = userContext ?: getCurrentUserContext()
            ?: return ResponseEntity.status(401).build()

        logger.info { "GET /store/favorites for user: ${context.userId}" }

        val favorites = favoriteService.getUserFavorites(context.userId)
        return ResponseEntity.ok(FavoritesListResponse(favorites))
    }

    /**
     * Adds product to favorites; handles creation failures
     */
    @Operation(summary = "Add product to favorites")
    @PostMapping
    fun addFavorite(
        @AuthenticationPrincipal userContext: UserContext?,
        @RequestBody request: AddFavoriteRequest
    ): ResponseEntity<FavoriteResponse> {
        val context = userContext ?: getCurrentUserContext()
            ?: return ResponseEntity.status(401).build()

        logger.info { "POST /store/favorites - adding product ${request.productId} for user: ${context.userId}" }

        return try {
            val favorite = favoriteService.addFavorite(context.userId, request)
            ResponseEntity.ok(favorite)
        } catch (e: FavoriteException) {
            logger.error(e) { "Failed to add favorite" }
            ResponseEntity.badRequest().build()
        }
    }

    @Operation(summary = "Remove product from favorites")
    @DeleteMapping("/{productId}")
    fun removeFavorite(
        @AuthenticationPrincipal userContext: UserContext?,
        @PathVariable productId: String
    ): ResponseEntity<Void> {
        val context = userContext ?: getCurrentUserContext()
            ?: return ResponseEntity.status(401).build()

        logger.info { "DELETE /store/favorites/$productId for user: ${context.userId}" }

        return try {
            favoriteService.removeFavorite(context.userId, productId)
            ResponseEntity.noContent().build()
        } catch (e: FavoriteNotFoundException) {
            ResponseEntity.notFound().build()
        }
    }

    @Operation(summary = "Check if product is in favorites")
    @GetMapping("/check/{productId}")
    fun checkFavorite(
        @AuthenticationPrincipal userContext: UserContext?,
        @PathVariable productId: String
    ): ResponseEntity<FavoriteCheckResponse> {
        val context = userContext ?: getCurrentUserContext()
            ?: return ResponseEntity.status(401).build()

        val isFavorite = favoriteService.isFavorite(context.userId, productId)
        return ResponseEntity.ok(FavoriteCheckResponse(productId, isFavorite))
    }

    @Operation(summary = "Check multiple products at once")
    @PostMapping("/check")
    fun checkFavorites(
        @AuthenticationPrincipal userContext: UserContext?,
        @RequestBody request: CheckFavoritesRequest
    ): ResponseEntity<CheckFavoritesResponse> {
        val context = userContext ?: getCurrentUserContext()
            ?: return ResponseEntity.status(401).build()

        val results = request.productIds.associateWith { productId ->
            favoriteService.isFavorite(context.userId, productId)
        }
        return ResponseEntity.ok(CheckFavoritesResponse(results))
    }

    @Operation(summary = "Update favorite settings")
    @PatchMapping("/{productId}")
    fun updateFavorite(
        @AuthenticationPrincipal userContext: UserContext?,
        @PathVariable productId: String,
        @RequestBody request: UpdateFavoriteRequest
    ): ResponseEntity<FavoriteResponse> {
        val context = userContext ?: getCurrentUserContext()
            ?: return ResponseEntity.status(401).build()

        logger.info { "PATCH /store/favorites/$productId for user: ${context.userId}" }

        return try {
            val updated = favoriteService.updateFavorite(context.userId, productId, request)
            ResponseEntity.ok(updated)
        } catch (e: FavoriteNotFoundException) {
            ResponseEntity.notFound().build()
        }
    }

    @Operation(summary = "Sync favorites from client localStorage")
    @PostMapping("/sync")
    fun syncFavorites(
        @AuthenticationPrincipal userContext: UserContext?,
        @RequestBody request: SyncFavoritesRequest
    ): ResponseEntity<FavoritesListResponse> {
        val context = userContext ?: getCurrentUserContext()
            ?: return ResponseEntity.status(401).build()

        logger.info { "POST /store/favorites/sync - syncing ${request.productIds.size} items for user: ${context.userId}" }

        return try {
            val favorites = favoriteService.syncFavorites(context.userId, request.productIds)
            ResponseEntity.ok(FavoritesListResponse(favorites))
        } catch (e: FavoriteException) {
            logger.error(e) { "Failed to sync favorites" }
            ResponseEntity.badRequest().build()
        }
    }
}

// Response DTOs
data class FavoritesListResponse(
    val favorites: List<FavoriteResponse>
)

data class CheckFavoritesRequest(
    val productIds: List<String>
)

data class CheckFavoritesResponse(
    val favorites: Map<String, Boolean>
)
