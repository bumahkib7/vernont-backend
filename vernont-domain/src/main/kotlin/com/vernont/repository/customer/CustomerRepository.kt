package com.vernont.repository.customer

import com.vernont.domain.customer.Customer
import org.springframework.data.jpa.repository.EntityGraph
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import java.time.Instant

@Repository
interface CustomerRepository : JpaRepository<Customer, String> {

    @EntityGraph(value = "Customer.full", type = EntityGraph.EntityGraphType.LOAD)
    override fun findById(id: String): java.util.Optional<Customer>

    @EntityGraph(value = "Customer.full", type = EntityGraph.EntityGraphType.LOAD)
    fun findByIdAndDeletedAtIsNull(id: String): Customer?

    @EntityGraph(value = "Customer.withAddresses", type = EntityGraph.EntityGraphType.LOAD)
    fun findWithAddressesById(id: String): Customer?

    @EntityGraph(value = "Customer.withAddresses", type = EntityGraph.EntityGraphType.LOAD)
    fun findWithAddressesByIdAndDeletedAtIsNull(id: String): Customer?

    @EntityGraph(value = "Customer.withGroups", type = EntityGraph.EntityGraphType.LOAD)
    fun findWithGroupsById(id: String): Customer?

    @EntityGraph(value = "Customer.withGroups", type = EntityGraph.EntityGraphType.LOAD)
    fun findWithGroupsByIdAndDeletedAtIsNull(id: String): Customer?

    @EntityGraph(value = "Customer.full", type = EntityGraph.EntityGraphType.LOAD)
    fun findWithFullDetailsByIdAndDeletedAtIsNull(id: String): Customer?

    fun findByEmail(email: String): Customer?

    fun findByEmailAndDeletedAtIsNull(email: String): Customer?

    @EntityGraph(value = "Customer.full", type = EntityGraph.EntityGraphType.LOAD)
    fun findWithFullDetailsByEmail(email: String): Customer?

    fun findByPhone(phone: String): Customer?

    fun findByPhoneAndDeletedAtIsNull(phone: String): Customer?

    fun findByHasAccount(hasAccount: Boolean): List<Customer>

    fun findByHasAccountAndDeletedAtIsNull(hasAccount: Boolean): List<Customer>

    fun findByDeletedAtIsNull(): List<Customer>

    fun existsByEmail(email: String): Boolean

    fun existsByEmailAndIdNot(email: String, id: String): Boolean

    @Query("SELECT c FROM Customer c JOIN c.groups g WHERE g.id = :groupId AND c.deletedAt IS NULL")
    fun findByGroupId(@Param("groupId") groupId: String): List<Customer>

    @Query("SELECT c FROM Customer c WHERE LOWER(c.firstName) LIKE LOWER(CONCAT('%', :searchTerm, '%')) OR LOWER(c.lastName) LIKE LOWER(CONCAT('%', :searchTerm, '%')) OR LOWER(c.email) LIKE LOWER(CONCAT('%', :searchTerm, '%')) AND c.deletedAt IS NULL")
    fun searchCustomers(@Param("searchTerm") searchTerm: String): List<Customer>

    @Query("SELECT COUNT(c) FROM Customer c WHERE c.hasAccount = true AND c.deletedAt IS NULL")
    fun countCustomersWithAccounts(): Long

    @Query("SELECT COUNT(c) FROM Customer c WHERE c.deletedAt IS NULL")
    fun countActiveCustomers(): Long

    @Query("SELECT c FROM Customer c WHERE c.user.id = :userId AND c.deletedAt IS NULL")
    fun findByUserIdAndDeletedAtIsNull(@Param("userId") userId: String): Customer?

    @Query("SELECT COUNT(c) FROM Customer c WHERE c.createdAt >= :since AND c.deletedAt IS NULL")
    fun countByCreatedAtAfter(@Param("since") since: Instant): Long

    @Query("SELECT COUNT(c) FROM Customer c WHERE c.createdAt >= :startDate AND c.createdAt < :endDate AND c.deletedAt IS NULL")
    fun countByCreatedAtBetween(@Param("startDate") startDate: Instant, @Param("endDate") endDate: Instant): Long
}
