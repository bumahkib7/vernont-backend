package com.vernont.repository.returns

import com.vernont.domain.returns.ReturnReasonConfig
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface ReturnReasonConfigRepository : JpaRepository<ReturnReasonConfig, String> {

    fun findByDeletedAtIsNull(): List<ReturnReasonConfig>

    fun findByDeletedAtIsNullOrderByDisplayOrderAsc(): List<ReturnReasonConfig>

    fun findByIdAndDeletedAtIsNull(id: String): ReturnReasonConfig?

    fun findByValueAndDeletedAtIsNull(value: String): ReturnReasonConfig?

    fun findByIsActiveTrueAndDeletedAtIsNullOrderByDisplayOrderAsc(): List<ReturnReasonConfig>

    fun existsByValueAndDeletedAtIsNull(value: String): Boolean

    fun existsByValueAndIdNotAndDeletedAtIsNull(value: String, id: String): Boolean
}
