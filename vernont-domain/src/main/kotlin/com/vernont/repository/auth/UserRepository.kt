package com.vernont.repository.auth

import com.vernont.domain.auth.User
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository

@Repository
interface UserRepository : JpaRepository<User, String> {

    fun findByEmail(email: String): User?

    @Query("SELECT u FROM User u LEFT JOIN FETCH u.roles WHERE LOWER(u.email) = LOWER(:email) AND u.deletedAt IS NULL")
    fun findByEmailWithRoles(@Param("email") email: String): User?

    @Query("SELECT u FROM User u LEFT JOIN FETCH u.roles WHERE u.id = :id AND u.deletedAt IS NULL")
    fun findByIdWithRoles(@Param("id") id: String): User?

    @Query("SELECT u FROM User u WHERE u.deletedAt IS NULL")
    fun findAllActive(): List<User>

    @Query("SELECT u FROM User u WHERE u.isActive = true AND u.deletedAt IS NULL")
    fun findAllActiveUsers(): List<User>

    fun existsByEmailIgnoreCase(email: String): Boolean

    /**
     * Finds users that have at least one role other than CUSTOMER/GUEST.
     * Effectively finds all internal staff users (Admin, CS, Warehouse, Developer).
     */
    @Query("""
        SELECT DISTINCT u FROM User u 
        JOIN u.roles r 
        WHERE r.name NOT IN ('CUSTOMER', 'GUEST') 
        AND u.deletedAt IS NULL
    """)
    fun findAllInternalUsers(): List<User>
}