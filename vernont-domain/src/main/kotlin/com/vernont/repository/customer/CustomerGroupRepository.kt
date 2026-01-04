package com.vernont.repository.customer

import com.vernont.domain.customer.CustomerGroup
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository

@Repository
interface CustomerGroupRepository : JpaRepository<CustomerGroup, String> {

    fun findByName(name: String): CustomerGroup?

    fun findByNameAndDeletedAtIsNull(name: String): CustomerGroup?

    fun findByIdAndDeletedAtIsNull(id: String): CustomerGroup?

    fun findByDeletedAtIsNull(): List<CustomerGroup>

    fun existsByName(name: String): Boolean

    fun existsByNameAndIdNot(name: String, id: String): Boolean

    @Query("SELECT cg FROM CustomerGroup cg WHERE LOWER(cg.name) LIKE LOWER(CONCAT('%', :searchTerm, '%')) AND cg.deletedAt IS NULL")
    fun searchByName(@Param("searchTerm") searchTerm: String): List<CustomerGroup>

    @Query("SELECT COUNT(cg) FROM CustomerGroup cg WHERE cg.deletedAt IS NULL")
    fun countActiveGroups(): Long

    @Query("SELECT cg FROM CustomerGroup cg JOIN cg.customers c WHERE c.id = :customerId AND cg.deletedAt IS NULL")
    fun findByCustomerId(@Param("customerId") customerId: String): List<CustomerGroup>
}
