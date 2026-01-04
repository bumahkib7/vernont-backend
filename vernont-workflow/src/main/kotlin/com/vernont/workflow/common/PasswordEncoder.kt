package com.vernont.workflow.common

import org.springframework.security.crypto.argon2.Argon2PasswordEncoder
import org.springframework.stereotype.Component

/**
 * Password encoder for workflow module.
 * 
 * This is a simple wrapper around Spring's Argon2PasswordEncoder to avoid
 * dependency on nexus-api module. Workflows should use this for password
 * encoding operations.
 */
@Component
class WorkflowPasswordEncoder {
    
    private val encoder = Argon2PasswordEncoder.defaultsForSpringSecurity_v5_8()
    
    /**
     * Encode a raw password
     */
    fun encode(rawPassword: String): String {
        return encoder.encode(rawPassword)!!
    }
    
    /**
     * Verify a raw password against an encoded password
     */
    fun matches(rawPassword: String, encodedPassword: String): Boolean {
        return encoder.matches(rawPassword, encodedPassword)
    }
}
