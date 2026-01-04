package com.vernont.api.controller

import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

data class SearchEvent(val q: String?, val total: Long?, val ts: Long?)

@RestController
@RequestMapping("/api/meta")
class MetaController {
    @PostMapping("/search-events")
    fun trackSearch(@RequestBody body: SearchEvent): ResponseEntity<Void> {
        // TODO: wire to analytics sink; for now just log
        println("[search-event] q='${body.q}' total=${body.total} ts=${body.ts}")
        return ResponseEntity.noContent().build()
    }
}
