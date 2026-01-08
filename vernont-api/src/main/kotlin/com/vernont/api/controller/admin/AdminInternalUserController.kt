package com.vernont.api.controller.admin

import com.vernont.api.dto.*
import com.vernont.application.auth.InternalUserService
import io.github.oshai.kotlinlogging.KotlinLogging
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.*
import jakarta.validation.Valid

private val logger = KotlinLogging.logger {}

@RestController
@RequestMapping("/api/admin/internal-users")
@Tag(name = "Internal Users", description = "Admin endpoints for managing internal staff users")
class AdminInternalUserController(
    private val internalUserService: InternalUserService
) {

    @Operation(summary = "List all internal users", description = "Retrieves all users with internal roles (Admin, Staff, etc.)")
    @ApiResponses(value = [
        ApiResponse(responseCode = "200", description = "Successfully retrieved users"),
        ApiResponse(responseCode = "403", description = "Access denied")
    ])
    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    fun getAllInternalUsers(
        @RequestParam(defaultValue = "100") limit: Int,
        @RequestParam(defaultValue = "0") offset: Int
    ): ResponseEntity<InternalUsersResponse> {
        logger.info { "Admin request to get all internal users" }
        val users = internalUserService.getAllInternalUsers()
        val userDtos = users.toInternalUserDtos()
        return ResponseEntity.ok(InternalUsersResponse(
            users = userDtos,
            count = userDtos.size,
            offset = offset,
            limit = limit
        ))
    }

    @Operation(summary = "Get user by ID", description = "Retrieves a single internal user by ID")
    @ApiResponses(value = [
        ApiResponse(responseCode = "200", description = "Successfully retrieved user"),
        ApiResponse(responseCode = "404", description = "User not found"),
        ApiResponse(responseCode = "403", description = "Access denied")
    ])
    @GetMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    fun getUserById(@PathVariable id: String): ResponseEntity<InternalUserDto> {
        logger.info { "Admin request to get user with id: $id" }
        return try {
            val user = internalUserService.getById(id)
            ResponseEntity.ok(user.toInternalUserDto())
        } catch (e: IllegalArgumentException) {
            logger.warn { "User not found: $id" }
            ResponseEntity.notFound().build()
        }
    }

    @Operation(summary = "Create internal user", description = "Creates a new internal user with specified roles")
    @ApiResponses(value = [
        ApiResponse(responseCode = "201", description = "User created successfully"),
        ApiResponse(responseCode = "400", description = "Invalid request or email exists"),
        ApiResponse(responseCode = "403", description = "Access denied")
    ])
    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    fun createInternalUser(@RequestBody request: CreateInternalUserRequest): ResponseEntity<InternalUserDto> {
        logger.info { "Admin request to create user: ${request.email}" }
        return try {
            val user = internalUserService.createInternalUser(
                email = request.email,
                password = request.password,
                firstName = request.firstName,
                lastName = request.lastName,
                roleNames = request.roles
            )
            ResponseEntity.status(HttpStatus.CREATED).body(user.toInternalUserDto())
        } catch (e: IllegalArgumentException) {
            logger.warn { "Failed to create user: ${e.message}" }
            ResponseEntity.badRequest().build()
        }
    }

    @Operation(summary = "Update internal user", description = "Updates an existing internal user")
    @ApiResponses(value = [
        ApiResponse(responseCode = "200", description = "User updated successfully"),
        ApiResponse(responseCode = "400", description = "Invalid request"),
        ApiResponse(responseCode = "404", description = "User not found"),
        ApiResponse(responseCode = "403", description = "Access denied")
    ])
    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    fun updateInternalUser(
        @PathVariable id: String,
        @RequestBody request: UpdateInternalUserRequest
    ): ResponseEntity<InternalUserDto> {
        logger.info { "Admin request to update user: $id" }
        return try {
            val user = internalUserService.updateInternalUser(
                userId = id,
                email = request.email,
                password = request.password,
                firstName = request.firstName,
                lastName = request.lastName,
                isActive = request.isActive,
                roleNames = request.roles
            )
            ResponseEntity.ok(user.toInternalUserDto())
        } catch (e: IllegalArgumentException) {
            logger.warn { "Failed to update user $id: ${e.message}" }
            ResponseEntity.badRequest().build()
        }
    }

    @Operation(summary = "Archive internal user", description = "Soft deletes (archives) an internal user. User can be restored later.")
    @ApiResponses(value = [
        ApiResponse(responseCode = "204", description = "User archived successfully"),
        ApiResponse(responseCode = "404", description = "User not found"),
        ApiResponse(responseCode = "403", description = "Access denied")
    ])
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    fun archiveInternalUser(@PathVariable id: String): ResponseEntity<Unit> {
        logger.info { "Admin request to archive user: $id" }
        return try {
            internalUserService.archiveInternalUser(id)
            ResponseEntity.noContent().build()
        } catch (e: IllegalArgumentException) {
            logger.warn { "Failed to archive user $id: ${e.message}" }
            ResponseEntity.notFound().build()
        }
    }

    @Operation(summary = "Permanently delete internal user", description = "PERMANENTLY deletes an internal user and all associated data. THIS CANNOT BE UNDONE.")
    @ApiResponses(value = [
        ApiResponse(responseCode = "204", description = "User permanently deleted"),
        ApiResponse(responseCode = "404", description = "User not found"),
        ApiResponse(responseCode = "403", description = "Access denied")
    ])
    @DeleteMapping("/{id}/permanent")
    @PreAuthorize("hasRole('ADMIN')")
    fun hardDeleteInternalUser(@PathVariable id: String): ResponseEntity<Unit> {
        logger.warn { "Admin request to PERMANENTLY delete user: $id" }
        return try {
            internalUserService.hardDeleteInternalUser(id)
            ResponseEntity.noContent().build()
        } catch (e: IllegalArgumentException) {
            logger.warn { "Failed to permanently delete user $id: ${e.message}" }
            ResponseEntity.notFound().build()
        }
    }

    @Operation(summary = "Restore archived user", description = "Restores a previously archived (soft-deleted) internal user")
    @ApiResponses(value = [
        ApiResponse(responseCode = "200", description = "User restored successfully"),
        ApiResponse(responseCode = "400", description = "User is not archived"),
        ApiResponse(responseCode = "404", description = "User not found"),
        ApiResponse(responseCode = "403", description = "Access denied")
    ])
    @PostMapping("/{id}/restore")
    @PreAuthorize("hasRole('ADMIN')")
    fun restoreInternalUser(@PathVariable id: String): ResponseEntity<InternalUserDto> {
        logger.info { "Admin request to restore user: $id" }
        return try {
            val user = internalUserService.restoreInternalUser(id)
            ResponseEntity.ok(user.toInternalUserDto())
        } catch (e: IllegalArgumentException) {
            logger.warn { "Failed to restore user $id: ${e.message}" }
            ResponseEntity.badRequest().build()
        }
    }
}
