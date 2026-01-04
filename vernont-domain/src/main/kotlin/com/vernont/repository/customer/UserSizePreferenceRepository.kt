package com.vernont.repository.customer

import com.vernont.domain.customer.UserSizePreference
import org.springframework.data.jpa.repository.JpaRepository

interface UserSizePreferenceRepository : JpaRepository<UserSizePreference, String> {
    fun findByUserIdAndDeletedAtIsNull(userId: String): List<UserSizePreference>
    fun findByUserIdAndSizeAndDeletedAtIsNull(userId: String, size: String): UserSizePreference?
}
