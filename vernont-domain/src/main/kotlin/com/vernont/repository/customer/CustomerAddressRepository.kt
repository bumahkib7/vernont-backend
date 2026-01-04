package com.vernont.repository.customer

import com.vernont.domain.customer.CustomerAddress
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository

@Repository
interface CustomerAddressRepository : JpaRepository<CustomerAddress, String> {

    fun findByCustomerId(customerId: String): List<CustomerAddress>

    fun findByCustomerIdAndDeletedAtIsNull(customerId: String): List<CustomerAddress>

    fun findByIdAndDeletedAtIsNull(id: String): CustomerAddress?

    fun findByDeletedAtIsNull(): List<CustomerAddress>

    @Query("SELECT ca FROM CustomerAddress ca WHERE ca.customer.id = :customerId AND ca.countryCode = :countryCode AND ca.deletedAt IS NULL")
    fun findByCustomerIdAndCountryCode(@Param("customerId") customerId: String, @Param("countryCode") countryCode: String): List<CustomerAddress>

    @Query("SELECT COUNT(ca) FROM CustomerAddress ca WHERE ca.customer.id = :customerId AND ca.deletedAt IS NULL")
    fun countByCustomerId(@Param("customerId") customerId: String): Long

    @Query("SELECT ca FROM CustomerAddress ca WHERE ca.customer.id = :customerId AND LOWER(ca.city) = LOWER(:city) AND ca.deletedAt IS NULL")
    fun findByCustomerIdAndCity(@Param("customerId") customerId: String, @Param("city") city: String): List<CustomerAddress>
}
