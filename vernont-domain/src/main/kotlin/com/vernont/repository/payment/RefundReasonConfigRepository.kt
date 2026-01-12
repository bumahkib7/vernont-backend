package com.vernont.repository.payment

import com.vernont.domain.payment.RefundReasonConfig
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface RefundReasonConfigRepository : JpaRepository<RefundReasonConfig, String> {

    fun findByDeletedAtIsNull(): List<RefundReasonConfig>

    fun findByDeletedAtIsNullOrderByDisplayOrderAsc(): List<RefundReasonConfig>

    fun findByIdAndDeletedAtIsNull(id: String): RefundReasonConfig?

    fun findByValueAndDeletedAtIsNull(value: String): RefundReasonConfig?

    fun findByIsActiveTrueAndDeletedAtIsNullOrderByDisplayOrderAsc(): List<RefundReasonConfig>

    fun existsByValueAndDeletedAtIsNull(value: String): Boolean

    fun existsByValueAndIdNotAndDeletedAtIsNull(value: String, id: String): Boolean
}
