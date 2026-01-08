package com.vernont.api.handler

import com.vernont.api.controller.admin.ProductAlreadyPublishedException
import com.vernont.api.rate.RateLimitException
import com.vernont.application.customer.CustomerNotFoundException
import com.vernont.application.customer.CustomerEmailAlreadyExistsException
import com.vernont.application.customer.CustomerAddressNotFoundException
import com.vernont.application.customer.CustomerGroupNotFoundException
import com.vernont.application.payment.PaymentException
import com.vernont.infrastructure.email.EmailException
import com.vernont.infrastructure.storage.StorageException
import io.github.oshai.kotlinlogging.KotlinLogging
import jakarta.validation.ConstraintViolationException
import org.apache.catalina.connector.ClientAbortException
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.access.AccessDeniedException
import org.springframework.security.authentication.BadCredentialsException
import org.springframework.http.converter.HttpMessageNotReadableException
import org.springframework.http.converter.HttpMessageNotWritableException
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.MissingServletRequestParameterException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.context.request.async.AsyncRequestNotUsableException
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException
import java.io.IOException
import java.time.Instant

private val logger = KotlinLogging.logger {}

/**
 * Standardized API error response.
 */
data class ApiError(
    val error: String,
    val message: String,
    val details: Map<String, Any?>? = null,
    val timestamp: Instant = Instant.now(),
    val requestId: String = java.util.UUID.randomUUID().toString()
)

@RestControllerAdvice
class GlobalExceptionHandler {

    // =====================================================
    // Validation & Bad Request Errors (400)
    // =====================================================

    @ExceptionHandler(IllegalArgumentException::class)
    fun handleIllegalArgument(ex: IllegalArgumentException): ResponseEntity<ApiError> {
        logger.warn { "Validation error: ${ex.message}" }
        val errorCode = deriveErrorCode(ex.message)
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(
            ApiError(
                error = errorCode,
                message = ex.message ?: "Invalid argument provided"
            )
        )
    }

    @ExceptionHandler(MethodArgumentNotValidException::class)
    fun handleValidationErrors(ex: MethodArgumentNotValidException): ResponseEntity<ApiError> {
        val fieldErrors = ex.bindingResult.fieldErrors.associate { it.field to (it.defaultMessage ?: "Invalid value") }
        logger.warn { "Validation failed: $fieldErrors" }
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(
            ApiError(
                error = "VALIDATION_ERROR",
                message = "Request validation failed",
                details = mapOf("fields" to fieldErrors)
            )
        )
    }

    @ExceptionHandler(ConstraintViolationException::class)
    fun handleConstraintViolation(ex: ConstraintViolationException): ResponseEntity<ApiError> {
        val violations = ex.constraintViolations.associate {
            it.propertyPath.toString() to it.message
        }
        logger.warn { "Constraint violations: $violations" }
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(
            ApiError(
                error = "VALIDATION_ERROR",
                message = "Request validation failed",
                details = mapOf("fields" to violations)
            )
        )
    }

    @ExceptionHandler(HttpMessageNotReadableException::class)
    fun handleUnreadableMessage(ex: HttpMessageNotReadableException): ResponseEntity<ApiError> {
        logger.warn { "Malformed request body: ${ex.message}" }
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(
            ApiError(
                error = "MALFORMED_REQUEST",
                message = "Request body is malformed or missing"
            )
        )
    }

    @ExceptionHandler(MissingServletRequestParameterException::class)
    fun handleMissingParam(ex: MissingServletRequestParameterException): ResponseEntity<ApiError> {
        logger.warn { "Missing parameter: ${ex.parameterName}" }
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(
            ApiError(
                error = "MISSING_PARAMETER",
                message = "Required parameter '${ex.parameterName}' is missing"
            )
        )
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException::class)
    fun handleTypeMismatch(ex: MethodArgumentTypeMismatchException): ResponseEntity<ApiError> {
        logger.warn { "Type mismatch for parameter '${ex.name}': ${ex.message}" }
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(
            ApiError(
                error = "INVALID_PARAMETER_TYPE",
                message = "Parameter '${ex.name}' has invalid type"
            )
        )
    }

    // =====================================================
    // Authentication & Authorization Errors (401, 403)
    // =====================================================

    @ExceptionHandler(com.vernont.domain.auth.UnauthorizedException::class)
    fun handleUnauthorized(ex: com.vernont.domain.auth.UnauthorizedException): ResponseEntity<ApiError> {
        logger.warn { "Unauthorized: ${ex.message}" }
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(
            ApiError(
                error = "UNAUTHORIZED",
                message = ex.message ?: "Authentication required"
            )
        )
    }

    @ExceptionHandler(BadCredentialsException::class)
    fun handleBadCredentials(ex: BadCredentialsException): ResponseEntity<ApiError> {
        logger.warn { "Bad credentials: ${ex.message}" }
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(
            ApiError(
                error = "INVALID_CREDENTIALS",
                message = "Invalid email or password"
            )
        )
    }

    @ExceptionHandler(AccessDeniedException::class)
    fun handleAccessDeniedException(ex: AccessDeniedException): ResponseEntity<ApiError> {
        logger.warn { "Access denied: ${ex.message}" }
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(
            ApiError(
                error = "ACCESS_DENIED",
                message = ex.message ?: "Access is denied"
            )
        )
    }

    // =====================================================
    // Not Found Errors (404)
    // =====================================================

    @ExceptionHandler(CustomerNotFoundException::class)
    fun handleCustomerNotFound(ex: CustomerNotFoundException): ResponseEntity<ApiError> {
        logger.warn { "Customer not found: ${ex.message}" }
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(
            ApiError(error = "CUSTOMER_NOT_FOUND", message = ex.message ?: "Customer not found")
        )
    }

    @ExceptionHandler(CustomerAddressNotFoundException::class)
    fun handleAddressNotFound(ex: CustomerAddressNotFoundException): ResponseEntity<ApiError> {
        logger.warn { "Customer address not found: ${ex.message}" }
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(
            ApiError(error = "ADDRESS_NOT_FOUND", message = ex.message ?: "Address not found")
        )
    }

    @ExceptionHandler(CustomerGroupNotFoundException::class)
    fun handleGroupNotFound(ex: CustomerGroupNotFoundException): ResponseEntity<ApiError> {
        logger.warn { "Customer group not found: ${ex.message}" }
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(
            ApiError(error = "CUSTOMER_GROUP_NOT_FOUND", message = ex.message ?: "Customer group not found")
        )
    }

    @ExceptionHandler(com.vernont.application.product.CollectionNotFoundException::class)
    fun handleCollectionNotFound(ex: com.vernont.application.product.CollectionNotFoundException): ResponseEntity<ApiError> {
        logger.warn { "Collection not found: ${ex.message}" }
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(
            ApiError(error = "COLLECTION_NOT_FOUND", message = ex.message ?: "Collection not found")
        )
    }

    @ExceptionHandler(com.vernont.application.product.ProductCategoryNotFoundException::class)
    fun handleProductCategoryNotFound(ex: com.vernont.application.product.ProductCategoryNotFoundException): ResponseEntity<ApiError> {
        logger.warn { "Product category not found: ${ex.message}" }
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(
            ApiError(error = "PRODUCT_CATEGORY_NOT_FOUND", message = ex.message ?: "Product category not found")
        )
    }

    @ExceptionHandler(com.vernont.application.product.ProductTagNotFoundException::class)
    fun handleProductTagNotFound(ex: com.vernont.application.product.ProductTagNotFoundException): ResponseEntity<ApiError> {
        logger.warn { "Product tag not found: ${ex.message}" }
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(
            ApiError(error = "PRODUCT_TAG_NOT_FOUND", message = ex.message ?: "Product tag not found")
        )
    }

    @ExceptionHandler(com.vernont.application.product.ProductNotFoundException::class)
    fun handleProductNotFound(ex: com.vernont.application.product.ProductNotFoundException): ResponseEntity<ApiError> {
        logger.warn { "Product not found: ${ex.message}" }
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(
            ApiError(error = "PRODUCT_NOT_FOUND", message = ex.message ?: "Product not found")
        )
    }

    @ExceptionHandler(com.vernont.application.order.OrderNotFoundException::class)
    fun handleOrderNotFound(ex: com.vernont.application.order.OrderNotFoundException): ResponseEntity<ApiError> {
        logger.warn { "Order not found: ${ex.message}" }
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(
            ApiError(error = "ORDER_NOT_FOUND", message = ex.message ?: "Order not found")
        )
    }

    @ExceptionHandler(com.vernont.application.inventory.InventoryItemNotFoundException::class)
    fun handleInventoryNotFound(ex: com.vernont.application.inventory.InventoryItemNotFoundException): ResponseEntity<ApiError> {
        logger.warn { "Inventory item not found: ${ex.message}" }
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(
            ApiError(error = "INVENTORY_ITEM_NOT_FOUND", message = ex.message ?: "Inventory item not found")
        )
    }

    @ExceptionHandler(NoSuchElementException::class)
    fun handleNoSuchElement(ex: NoSuchElementException): ResponseEntity<ApiError> {
        logger.warn { "Resource not found: ${ex.message}" }
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(
            ApiError(error = "NOT_FOUND", message = ex.message ?: "Requested resource not found")
        )
    }

    // =====================================================
    // Conflict Errors (409)
    // =====================================================

    @ExceptionHandler(ProductAlreadyPublishedException::class)
    fun handleAlreadyPublished(ex: ProductAlreadyPublishedException): ResponseEntity<ApiError> {
        logger.warn { "Product already published: ${ex.message}" }
        return ResponseEntity.status(HttpStatus.CONFLICT).body(
            ApiError(
                error = "PRODUCT_ALREADY_PUBLISHED",
                message = ex.message ?: "Product is already published",
                details = mapOf("productId" to ex.productId)
            )
        )
    }

    @ExceptionHandler(CustomerEmailAlreadyExistsException::class)
    fun handleCustomerEmailExists(ex: CustomerEmailAlreadyExistsException): ResponseEntity<ApiError> {
        logger.warn { "Customer email already exists: ${ex.message}" }
        return ResponseEntity.status(HttpStatus.CONFLICT).body(
            ApiError(error = "CUSTOMER_EMAIL_ALREADY_EXISTS", message = ex.message ?: "Email already in use")
        )
    }

    @ExceptionHandler(com.vernont.application.product.ProductHandleAlreadyExistsException::class)
    fun handleProductHandleExists(ex: com.vernont.application.product.ProductHandleAlreadyExistsException): ResponseEntity<ApiError> {
        logger.warn { "Product handle already exists: ${ex.message}" }
        return ResponseEntity.status(HttpStatus.CONFLICT).body(
            ApiError(error = "PRODUCT_HANDLE_ALREADY_EXISTS", message = ex.message ?: "Product handle already in use")
        )
    }

    @ExceptionHandler(com.vernont.application.inventory.DuplicateSkuException::class)
    fun handleDuplicateSku(ex: com.vernont.application.inventory.DuplicateSkuException): ResponseEntity<ApiError> {
        logger.warn { "Duplicate SKU: ${ex.message}" }
        return ResponseEntity.status(HttpStatus.CONFLICT).body(
            ApiError(error = "DUPLICATE_SKU", message = ex.message ?: "SKU already exists")
        )
    }

    // =====================================================
    // Business Logic Errors (422 Unprocessable Entity)
    // =====================================================

    @ExceptionHandler(com.vernont.application.inventory.InsufficientInventoryException::class)
    fun handleInsufficientInventory(ex: com.vernont.application.inventory.InsufficientInventoryException): ResponseEntity<ApiError> {
        logger.warn { "Insufficient inventory: ${ex.message}" }
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(
            ApiError(error = "INSUFFICIENT_INVENTORY", message = ex.message ?: "Insufficient inventory")
        )
    }

    @ExceptionHandler(com.vernont.application.order.OrderUpdateNotAllowedException::class)
    fun handleOrderUpdateNotAllowed(ex: com.vernont.application.order.OrderUpdateNotAllowedException): ResponseEntity<ApiError> {
        logger.warn { "Order update not allowed: ${ex.message}" }
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(
            ApiError(error = "ORDER_UPDATE_NOT_ALLOWED", message = ex.message ?: "Order cannot be updated")
        )
    }

    @ExceptionHandler(com.vernont.application.order.OrderCancellationNotAllowedException::class)
    fun handleOrderCancellationNotAllowed(ex: com.vernont.application.order.OrderCancellationNotAllowedException): ResponseEntity<ApiError> {
        logger.warn { "Order cancellation not allowed: ${ex.message}" }
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(
            ApiError(error = "ORDER_CANCELLATION_NOT_ALLOWED", message = ex.message ?: "Order cannot be cancelled")
        )
    }

    @ExceptionHandler(com.vernont.application.cart.EmptyCartException::class)
    fun handleEmptyCart(ex: com.vernont.application.cart.EmptyCartException): ResponseEntity<ApiError> {
        logger.warn { "Empty cart: ${ex.message}" }
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(
            ApiError(error = "EMPTY_CART", message = ex.message ?: "Cart is empty")
        )
    }

    // =====================================================
    // Rate Limiting (429)
    // =====================================================

    @ExceptionHandler(RateLimitException::class)
    fun handleRateLimit(ex: RateLimitException): ResponseEntity<ApiError> {
        logger.warn { "Rate limit exceeded: ${ex.message}" }
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).body(
            ApiError(error = "RATE_LIMIT_EXCEEDED", message = ex.message ?: "Too many requests")
        )
    }

    // =====================================================
    // External Service Errors (502 Bad Gateway)
    // =====================================================

    @ExceptionHandler(EmailException::class)
    fun handleEmailException(ex: EmailException): ResponseEntity<ApiError> {
        val requestId = java.util.UUID.randomUUID().toString()
        logger.error(ex) { "Email service error [requestId=$requestId]: ${ex.message}" }
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(
            ApiError(
                error = "EMAIL_SERVICE_ERROR",
                message = "Failed to send email. Please try again later.",
                requestId = requestId
            )
        )
    }

    @ExceptionHandler(PaymentException::class)
    fun handlePaymentException(ex: PaymentException): ResponseEntity<ApiError> {
        val requestId = java.util.UUID.randomUUID().toString()
        logger.error(ex) { "Payment service error [requestId=$requestId]: ${ex.message}" }
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(
            ApiError(
                error = "PAYMENT_SERVICE_ERROR",
                message = "Payment processing failed. Please try again later.",
                requestId = requestId
            )
        )
    }

    @ExceptionHandler(StorageException::class)
    fun handleStorageException(ex: StorageException): ResponseEntity<ApiError> {
        val requestId = java.util.UUID.randomUUID().toString()
        logger.error(ex) { "Storage service error [requestId=$requestId]: ${ex.message}" }
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(
            ApiError(
                error = "STORAGE_SERVICE_ERROR",
                message = "File operation failed. Please try again later.",
                requestId = requestId
            )
        )
    }

    // =====================================================
    // Client Disconnect (Silent)
    // =====================================================

    @ExceptionHandler(ClientAbortException::class, AsyncRequestNotUsableException::class)
    fun handleClientAbort(ex: Exception): ResponseEntity<ApiError>? {
        logger.debug { "Client disconnected before response could be sent: ${ex.message}" }
        return null
    }

    @ExceptionHandler(HttpMessageNotWritableException::class)
    fun handleMessageWriteError(ex: HttpMessageNotWritableException): ResponseEntity<ApiError>? {
        if (isClientDisconnect(ex)) {
            logger.debug { "Client disconnected during response write: ${ex.message}" }
            return null
        }
        val requestId = java.util.UUID.randomUUID().toString()
        logger.error(ex) { "Failed to write response [requestId=$requestId]: ${ex.message}" }
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(
            ApiError(
                error = "RESPONSE_WRITE_FAILED",
                message = "Failed to write response",
                requestId = requestId
            )
        )
    }

    private fun isClientDisconnect(ex: Throwable): Boolean {
        var current: Throwable? = ex
        while (current != null) {
            if (current is ClientAbortException || current is AsyncRequestNotUsableException) return true
            if (current is IOException && current.message?.contains("Broken pipe", ignoreCase = true) == true) return true
            current = current.cause
        }
        return false
    }

    // =====================================================
    // Database Errors (500)
    // =====================================================

    @ExceptionHandler(org.springframework.dao.DataIntegrityViolationException::class)
    fun handleDataIntegrity(ex: org.springframework.dao.DataIntegrityViolationException): ResponseEntity<ApiError> {
        val requestId = java.util.UUID.randomUUID().toString()
        logger.error(ex) { "Data integrity violation [requestId=$requestId]" }

        // Try to provide a more helpful message for common constraint violations
        val message = when {
            ex.message?.contains("unique constraint", ignoreCase = true) == true ||
            ex.message?.contains("duplicate key", ignoreCase = true) == true ->
                "A record with this value already exists"
            ex.message?.contains("foreign key", ignoreCase = true) == true ->
                "Referenced record does not exist or cannot be deleted"
            else -> "A database constraint was violated"
        }

        return ResponseEntity.status(HttpStatus.CONFLICT).body(
            ApiError(
                error = "DATA_INTEGRITY_ERROR",
                message = message,
                requestId = requestId
            )
        )
    }

    // =====================================================
    // Fallback Handler (500)
    // =====================================================

    @ExceptionHandler(Exception::class)
    fun handleGenericException(ex: Exception): ResponseEntity<ApiError> {
        val requestId = java.util.UUID.randomUUID().toString()
        logger.error(ex) { "Unhandled exception occurred [requestId=$requestId]: ${ex.message}" }
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(
            ApiError(
                error = "INTERNAL_SERVER_ERROR",
                message = "An unexpected error occurred. Please try again later.",
                requestId = requestId
            )
        )
    }

    // =====================================================
    // Helpers
    // =====================================================

    /**
     * Derive a more specific error code from the exception message.
     * Converts messages like "User with email 'x' already exists" to "USER_ALREADY_EXISTS"
     */
    private fun deriveErrorCode(message: String?): String {
        if (message == null) return "INVALID_ARGUMENT"
        return when {
            message.contains("already exists", ignoreCase = true) -> "ALREADY_EXISTS"
            message.contains("not found", ignoreCase = true) -> "NOT_FOUND"
            message.contains("required", ignoreCase = true) -> "REQUIRED_FIELD_MISSING"
            message.contains("invalid", ignoreCase = true) -> "INVALID_VALUE"
            message.contains("role", ignoreCase = true) -> "INVALID_ROLE"
            else -> "INVALID_ARGUMENT"
        }
    }
}
