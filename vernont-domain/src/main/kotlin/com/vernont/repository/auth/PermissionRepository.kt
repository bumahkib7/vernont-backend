package com.vernont.repository.auth

import com.vernont.domain.auth.Permission
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository

@Repository
interface PermissionRepository : JpaRepository<Permission, String> {

    fun findByName(name: String): Permission?

    @Query("SELECT p FROM Permission p WHERE p.deletedAt IS NULL")
    fun findAllActive(): List<Permission>

    fun existsByName(name: String): Boolean

    fun findByResource(resource: String): List<Permission>

    fun findByAction(action: String): List<Permission>
}