package com.vernont.api.auth

import com.vernont.repository.auth.UserRepository
import com.vernont.repository.customer.CustomerRepository
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.time.Instant

@RestController
@RequestMapping("/auth")
class AccountDeletionController(
    private val userRepository: UserRepository,
    private val customerRepository: CustomerRepository,
    private val jwtTokenProvider: JwtTokenProvider,
) {
    private val logger = KotlinLogging.logger {}

    @DeleteMapping("/customer/delete")
    @Transactional
    fun deleteAccount(@RequestHeader("Authorization") authorization: String?): ResponseEntity<Any> {
        return try {
            if (authorization.isNullOrBlank() || !authorization.startsWith("Bearer ")) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(
                    mapOf("error" to "UNAUTHORIZED", "message" to "Missing or invalid token")
                )
            }
            val token = authorization.removePrefix("Bearer ").trim()
            if (!jwtTokenProvider.validateToken(token)) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(
                    mapOf("error" to "INVALID_TOKEN", "message" to "Token is invalid or expired")
                )
            }
            val userId = jwtTokenProvider.getUserIdFromToken(token)
            val user = userRepository.findByIdWithRoles(userId)
                ?: return ResponseEntity.status(HttpStatus.NOT_FOUND).body(
                    mapOf("error" to "USER_NOT_FOUND", "message" to "User not found")
                )

            // Soft delete: deactivate user and mark linked customer as deleted
            user.isActive = false
            user.deletedAt = Instant.now()
            userRepository.save(user)

            val customer = customerRepository.findByUserIdAndDeletedAtIsNull(user.id)
            if (customer != null) {
                customer.deletedAt = Instant.now()
                customerRepository.save(customer)
            }

            logger.info { "Deleted account for userId=$userId" }
            ResponseEntity.ok(mapOf("status" to "deleted"))
        } catch (e: Exception) {
            logger.error(e) { "Account deletion failed" }
            ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(
                mapOf("error" to "DELETE_FAILED", "message" to "Unable to delete account right now")
            )
        }
    }
}
