package com.vernont.application.config

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.plus
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Primary

@Configuration
class CoroutineConfig {

    /**
     * Shared application coroutine scope for background work (IO + DB safe).
     */
    @Bean
    @Primary
    fun applicationCoroutineScope(): CoroutineScope =
        CoroutineScope(SupervisorJob()) + Dispatchers.IO.limitedParallelism(8)
}
