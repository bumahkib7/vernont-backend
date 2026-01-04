package com.vernont.repository.payment

import com.vernont.domain.payment.PaymentProvider
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository

@Repository
interface PaymentProviderRepository : JpaRepository<PaymentProvider, String> {

    fun findByName(name: String): PaymentProvider?

    fun findByNameAndDeletedAtIsNull(name: String): PaymentProvider?

    fun findByIdAndDeletedAtIsNull(id: String): PaymentProvider?

    fun findByDeletedAtIsNull(): List<PaymentProvider>

    @Query("SELECT pp FROM PaymentProvider pp WHERE pp.isActive = true AND pp.deletedAt IS NULL")
    fun findAllActive(): List<PaymentProvider>

    @Query("SELECT COUNT(pp) FROM PaymentProvider pp WHERE pp.deletedAt IS NULL")
    fun countActiveProviders(): Long

    @Query("SELECT COUNT(pp) FROM PaymentProvider pp WHERE pp.isActive = true AND pp.deletedAt IS NULL")
    fun countEnabledProviders(): Long

    fun existsByName(name: String): Boolean

    fun existsByNameAndIdNot(name: String, id: String): Boolean

    @Query(
        """
        SELECT p
        FROM PaymentProvider p
        WHERE p.id IN :paymentProviderIds
          AND p.deletedAt IS NULL
        """
    )
    fun findByIdIn(
        @Param("paymentProviderIds") paymentProviderIds: List<String>
    ): List<PaymentProvider>
}
