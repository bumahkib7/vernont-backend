package com.vernont.infrastructure.audit

import org.springframework.context.annotation.Primary
import org.springframework.data.domain.AuditorAware
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Component
import java.util.*

/**
 * Provides the current auditor (user) for JPA auditing.
 * Automatically populates createdBy and updatedBy fields in BaseEntity.
 */
@Component
@Primary
class AuditorAwareImpl : AuditorAware<String> {

    override fun getCurrentAuditor(): Optional<String> {
        return try {
            val authentication = SecurityContextHolder.getContext().authentication

            if (authentication == null || !authentication.isAuthenticated) {
                // No authenticated user - use system
                Optional.of("SYSTEM")
            } else {
                // Return authenticated user's email/username
                Optional.of(authentication.name)
            }
        } catch (e: Exception) {
            // Fallback to SYSTEM on any error
            Optional.of("SYSTEM")
        }
    }
}
