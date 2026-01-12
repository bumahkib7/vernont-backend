package com.vernont.repository.security

import com.vernont.domain.security.SecuritySettings
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface SecuritySettingsRepository : JpaRepository<SecuritySettings, String> {

    fun findByIdAndDeletedAtIsNull(id: String): SecuritySettings?
}
