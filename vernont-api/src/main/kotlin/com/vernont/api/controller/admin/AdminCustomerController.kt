package com.vernont.api.controller.admin

import com.vernont.repository.customer.CustomerRepository
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/admin/customers")
class AdminCustomerController(
    private val customerRepository: CustomerRepository
) {

    @GetMapping
    fun listCustomers(
        @RequestParam(required = false, defaultValue = "20") limit: Int,
        @RequestParam(required = false, defaultValue = "0") offset: Int,
        @RequestParam(required = false) q: String?,
        @RequestParam(required = false) fields: String?,
        @RequestParam(required = false) order: String?
    ): ResponseEntity<Any> {
        // Get all customers
        val allCustomers = customerRepository.findAll()

        // Get total count
        val count = allCustomers.size

        // Apply pagination
        val paginatedCustomers = allCustomers
            .drop(offset)
            .take(limit.coerceAtMost(100)) // Max 100 items per request

        // Return Medusa-compatible format
        return ResponseEntity.ok(mapOf(
            "customers" to paginatedCustomers,
            "limit" to limit,
            "offset" to offset,
            "count" to count
        ))
    }

    @GetMapping("/{id}")
    fun getCustomer(@PathVariable id: String): ResponseEntity<Any> {
        val customer = customerRepository.findById(id)
            .orElse(null)
            ?: return ResponseEntity.notFound().build()

        return ResponseEntity.ok(customer)
    }
}
