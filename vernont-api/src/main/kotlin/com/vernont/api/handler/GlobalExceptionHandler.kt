package com.vernont.api.handler

import com.vernont.api.controller.admin.ProductAlreadyPublishedException
import com.vernont.application.customer.CustomerNotFoundException
import com.vernont.application.customer.CustomerEmailAlreadyExistsException
import com.vernont.application.customer.CustomerAddressNotFoundException
import com.vernont.application.customer.CustomerGroupNotFoundException
import io.github.oshai.kotlinlogging.KotlinLogging
import org.apache.catalina.connector.ClientAbortException
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.access.AccessDeniedException
import org.springframework.http.converter.HttpMessageNotWritableException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.context.request.async.AsyncRequestNotUsableException
import java.io.IOException

private val logger = KotlinLogging.logger {}

@RestControllerAdvice
class GlobalExceptionHandler {

    @ExceptionHandler(ProductAlreadyPublishedException::class)
    fun handleAlreadyPublished(ex: ProductAlreadyPublishedException): ResponseEntity<Map<String, String?>> {
        logger.warn(ex) { "Product already published: ${ex.message}" }
        val body = mapOf(
            "error" to "PRODUCT_ALREADY_PUBLISHED",
            "message" to ex.message,
            "productId" to ex.productId
        )
        return ResponseEntity.status(HttpStatus.CONFLICT).body(body)
    }

    @ExceptionHandler(CustomerNotFoundException::class)
    fun handleCustomerNotFound(ex: CustomerNotFoundException): ResponseEntity<Map<String, String?>> {
        logger.warn(ex) { "Customer not found: ${ex.message}" }
        val body = mapOf(
            "error" to "CUSTOMER_NOT_FOUND",
            "message" to ex.message
        )
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(body)
    }

    @ExceptionHandler(CustomerEmailAlreadyExistsException::class)
    fun handleCustomerEmailExists(ex: CustomerEmailAlreadyExistsException): ResponseEntity<Map<String, String?>> {
        logger.warn(ex) { "Customer email already exists: ${ex.message}" }
        val body = mapOf(
            "error" to "CUSTOMER_EMAIL_ALREADY_EXISTS",
            "message" to ex.message
        )
        return ResponseEntity.status(HttpStatus.CONFLICT).body(body)
    }

    @ExceptionHandler(CustomerAddressNotFoundException::class)
    fun handleAddressNotFound(ex: CustomerAddressNotFoundException): ResponseEntity<Map<String, String?>> {
        logger.warn(ex) { "Customer address not found: ${ex.message}" }
        val body = mapOf(
            "error" to "ADDRESS_NOT_FOUND",
            "message" to ex.message
        )
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(body)
    }

    @ExceptionHandler(CustomerGroupNotFoundException::class)
    fun handleGroupNotFound(ex: CustomerGroupNotFoundException): ResponseEntity<Map<String, String?>> {
        logger.warn(ex) { "Customer group not found: ${ex.message}" }
        val body = mapOf(
            "error" to "CUSTOMER_GROUP_NOT_FOUND",
            "message" to ex.message
        )
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(body)
    }

    @ExceptionHandler(com.vernont.application.product.CollectionNotFoundException::class)
    fun handleCollectionNotFound(ex: com.vernont.application.product.CollectionNotFoundException): ResponseEntity<Map<String, String?>> {
        logger.warn(ex) { "Collection not found: ${ex.message}" }
        val body = mapOf(
            "error" to "COLLECTION_NOT_FOUND",
            "message" to ex.message
        )
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(body)
    }

    @ExceptionHandler(com.vernont.application.product.ProductCategoryNotFoundException::class)
    fun handleProductCategoryNotFound(ex: com.vernont.application.product.ProductCategoryNotFoundException): ResponseEntity<Map<String, String?>> {
        logger.warn(ex) { "Product category not found: ${ex.message}" }
        val body = mapOf(
            "error" to "PRODUCT_CATEGORY_NOT_FOUND",
            "message" to ex.message
        )
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(body)
    }

    @ExceptionHandler(com.vernont.application.product.ProductTagNotFoundException::class)
    fun handleProductTagNotFound(ex: com.vernont.application.product.ProductTagNotFoundException): ResponseEntity<Map<String, String?>> {
        logger.warn(ex) { "Product tag not found: ${ex.message}" }
        val body = mapOf(
            "error" to "PRODUCT_TAG_NOT_FOUND",
            "message" to ex.message
        )
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(body)
    }

    @ExceptionHandler(AccessDeniedException::class)
    fun handleAccessDeniedException(ex: AccessDeniedException): ResponseEntity<Map<String, String?>> {
        logger.warn(ex) { "Access denied: ${ex.message}" }
        val body = mapOf(
            "error" to "ACCESS_DENIED",
            "message" to (ex.message ?: "Access is denied")
        )
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(body)
    }

    @ExceptionHandler(ClientAbortException::class, AsyncRequestNotUsableException::class)
    fun handleClientAbort(ex: Exception): ResponseEntity<Map<String, String?>>? {
        logger.debug { "Client disconnected before response could be sent: ${ex.message}" }
        return null
    }

    @ExceptionHandler(HttpMessageNotWritableException::class)
    fun handleMessageWriteError(ex: HttpMessageNotWritableException): ResponseEntity<Map<String, String?>>? {
        if (isClientDisconnect(ex)) {
            logger.debug { "Client disconnected during response write: ${ex.message}" }
            return null
        }
        logger.error(ex) { "Failed to write response: ${ex.message}" }
        val body = mapOf(
            "error" to "RESPONSE_WRITE_FAILED",
            "message" to (ex.message ?: "Failed to write response")
        )
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(body)
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

    @ExceptionHandler(org.springframework.dao.DataIntegrityViolationException::class)
    fun handleDataIntegrity(ex: org.springframework.dao.DataIntegrityViolationException): ResponseEntity<Map<String, String?>> {
        val requestId = java.util.UUID.randomUUID().toString()
        logger.error(ex) { "Data integrity violation [requestId=$requestId]" }
        val body = mapOf(
            "error" to "INTERNAL_SERVER_ERROR",
            "message" to "An unexpected error occurred. Please try again later.",
            "requestId" to requestId
        )
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(body)
    }

    @ExceptionHandler(Exception::class)
    fun handleGenericException(ex: Exception): ResponseEntity<Map<String, String?>> {
        val requestId = java.util.UUID.randomUUID().toString()
        logger.error(ex) { "Unhandled exception occurred [requestId=$requestId]: ${ex.message}" }
        val body = mapOf(
            "error" to "INTERNAL_SERVER_ERROR",
            "message" to "An unexpected error occurred. Please try again later.",
            "requestId" to requestId
        )
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(body)
    }
}
