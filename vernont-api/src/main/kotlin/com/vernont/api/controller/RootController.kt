package com.vernont.api.controller

import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * Basic root endpoint to avoid NoResourceFoundException when hitting "/".
 * Returns a lightweight status payload.
 */
@RestController
@RequestMapping("/")
class RootController {
    @GetMapping
    fun root(): ResponseEntity<Map<String, String>> =
        ResponseEntity.ok(
            mapOf(
                "status" to "ok",
                "message" to "Nexus Commerce API"
            )
        )
}
