package com.vernont.repository.auth

import com.vernont.domain.auth.Role
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository

@Repository
interface RoleRepository : JpaRepository<Role, String> {

    fun findByName(name: String): Role?

    @Query("SELECT r FROM Role r LEFT JOIN FETCH r.permissions WHERE r.name = :name AND r.deletedAt IS NULL")
    fun findByNameWithPermissions(@Param("name") name: String): Role?

    @Query("SELECT r FROM Role r WHERE r.deletedAt IS NULL")
    fun findAllActive(): List<Role>

    fun existsByName(name: String): Boolean

    @Query(
        """
    SELECT r
    FROM Role r
    WHERE r.name IN :requiredRoleNames
    AND r.deletedAt IS NULL
    """
    )
    fun findByNameIn(@Param("requiredRoleNames") requiredRoleNames: List<String>): Set<Role>
}