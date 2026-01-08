package com.vernont.application.inventory

import com.vernont.repository.inventory.InventoryItemRepository
import com.vernont.repository.product.ProductVariantRepository
import org.springframework.stereotype.Service
import java.security.SecureRandom
import java.time.LocalDate
import java.util.concurrent.atomic.AtomicLong

/**
 * Service for generating unique SKUs in retail-standard numeric format.
 *
 * Format: YYMMDD + 6-digit sequence = 12 digits total
 * Example: 260105000001 (Jan 5, 2026, sequence 1)
 *
 * This format:
 * - Is scannable by barcode readers
 * - Follows retail industry standards
 * - Includes date for easy tracking
 * - Supports 999,999 products per day
 */
@Service
class SkuGeneratorService(
    private val inventoryItemRepository: InventoryItemRepository,
    private val productVariantRepository: ProductVariantRepository
) {

    private val secureRandom = SecureRandom()
    private val sequenceCounter = AtomicLong(0)
    private var lastDate: String = ""

    /**
     * Generate a unique numeric SKU.
     * Format: YYMMDD + 6-digit random/sequence number
     */
    @Synchronized
    fun generateSku(): String {
        val today = LocalDate.now()
        val datePrefix = String.format("%02d%02d%02d",
            today.year % 100,
            today.monthValue,
            today.dayOfMonth
        )

        // Reset counter if date changed
        if (datePrefix != lastDate) {
            lastDate = datePrefix
            sequenceCounter.set(0)
        }

        // Try to generate a unique SKU
        repeat(10) { // Max 10 attempts
            val sequence = sequenceCounter.incrementAndGet()
            val sku = if (sequence <= 999999) {
                // Use sequential for first million
                "$datePrefix${String.format("%06d", sequence)}"
            } else {
                // Fall back to random for overflow
                "$datePrefix${String.format("%06d", secureRandom.nextInt(1000000))}"
            }

            if (!skuExists(sku)) {
                return sku
            }
        }

        // Ultimate fallback: fully random 12-digit
        return generateRandomSku()
    }

    /**
     * Generate a unique SKU with a prefix.
     * Format: PREFIX + 8-digit number
     * Example: VRN00000001
     */
    fun generateSkuWithPrefix(prefix: String = "VRN"): String {
        repeat(10) {
            val number = String.format("%08d", secureRandom.nextInt(100000000))
            val sku = "$prefix$number"
            if (!skuExists(sku)) {
                return sku
            }
        }
        return generateRandomSku()
    }

    /**
     * Generate a fully random 12-digit SKU (fallback).
     */
    private fun generateRandomSku(): String {
        repeat(100) {
            val sku = String.format("%012d", secureRandom.nextLong(1000000000000L).coerceAtLeast(0))
            if (!skuExists(sku)) {
                return sku
            }
        }
        throw IllegalStateException("Failed to generate unique SKU after 100 attempts")
    }

    /**
     * Check if SKU already exists in the system.
     */
    fun skuExists(sku: String): Boolean {
        // Check inventory items
        if (inventoryItemRepository.findBySkuAndDeletedAtIsNull(sku) != null) {
            return true
        }
        // Check product variants directly
        if (productVariantRepository.findBySkuAndDeletedAtIsNull(sku) != null) {
            return true
        }
        return false
    }

    /**
     * Generate EAN-13 barcode (European Article Number).
     * Format: Country(2) + Manufacturer(5) + Product(5) + Check(1)
     * Uses UK prefix (50) by default.
     */
    fun generateEan13(countryPrefix: String = "50"): String {
        repeat(10) {
            // Generate 10 random digits after country prefix
            val productCode = StringBuilder(countryPrefix)
            repeat(10) {
                productCode.append(secureRandom.nextInt(10))
            }

            // Calculate check digit
            val checkDigit = calculateEan13CheckDigit(productCode.toString())
            val ean = productCode.toString() + checkDigit

            // Verify uniqueness (check against barcode field in variants)
            if (!barcodeExists(ean)) {
                return ean
            }
        }
        throw IllegalStateException("Failed to generate unique EAN-13 after 10 attempts")
    }

    /**
     * Calculate EAN-13 check digit using the standard algorithm.
     */
    private fun calculateEan13CheckDigit(first12Digits: String): Int {
        require(first12Digits.length == 12) { "EAN-13 requires exactly 12 digits before check digit" }

        var sum = 0
        for (i in 0 until 12) {
            val digit = first12Digits[i].digitToInt()
            sum += if (i % 2 == 0) digit else digit * 3
        }
        return (10 - (sum % 10)) % 10
    }

    /**
     * Check if barcode already exists.
     */
    fun barcodeExists(barcode: String): Boolean {
        // Check barcode field (EAN is typically stored in barcode field)
        return productVariantRepository.findByBarcodeAndDeletedAtIsNull(barcode) != null
    }

    /**
     * Validate EAN-13 format and check digit.
     */
    fun isValidEan13(barcode: String): Boolean {
        if (!barcode.matches(Regex("^\\d{13}$"))) return false

        val first12 = barcode.substring(0, 12)
        val providedCheck = barcode[12].digitToInt()
        val calculatedCheck = calculateEan13CheckDigit(first12)

        return providedCheck == calculatedCheck
    }
}
