package com.vernont.api.config

import com.vernont.api.websocket.WebSocketAuthInterceptor
import com.vernont.api.websocket.WebSocketChannelInterceptor
import com.vernont.api.websocket.WebSocketRateLimitInterceptor
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Primary
import org.springframework.messaging.simp.config.ChannelRegistration
import org.springframework.messaging.simp.config.MessageBrokerRegistry
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker
import org.springframework.web.socket.config.annotation.StompEndpointRegistry
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer
import org.springframework.beans.factory.annotation.Value
import java.util.concurrent.Executor
import java.util.concurrent.ThreadPoolExecutor

@Configuration
@EnableWebSocketMessageBroker
class WebSocketConfig(
    @Value("\${spring.security.cors.allowed-origins:http://localhost:3000,http://localhost:3001,http://localhost:8080,http://127.0.0.1:3000,http://127.0.0.1:3001,http://127.0.0.1:8080}")
    private val corsAllowedOrigins: List<String>,
    @Value("\${spring.security.cors.allowed-origin-patterns:https://*.vernont.com,https://vernont.com,https://*.vercel.app}")
    private val corsAllowedOriginPatterns: List<String>,
    private val webSocketAuthInterceptor: WebSocketAuthInterceptor,
    private val webSocketChannelInterceptor: WebSocketChannelInterceptor,
    private val webSocketRateLimitInterceptor: WebSocketRateLimitInterceptor
) : WebSocketMessageBrokerConfigurer {

    override fun registerStompEndpoints(registry: StompEndpointRegistry) {
        registry.addEndpoint("/ws")
            .setAllowedOrigins(*corsAllowedOrigins.toTypedArray())
            .setAllowedOriginPatterns(*corsAllowedOriginPatterns.toTypedArray())
            .addInterceptors(webSocketAuthInterceptor)  // Add JWT authentication
            .withSockJS() // Enable SockJS fallback options
    }

    override fun configureMessageBroker(config: MessageBrokerRegistry) {
        config.enableSimpleBroker("/topic", "/queue") // Enable a simple in-memory broker for public topics and private queues
        config.setApplicationDestinationPrefixes("/app") // Prefix for messages from clients to the server
    }

    override fun configureClientInboundChannel(registration: ChannelRegistration) {
        registration.interceptors(
            webSocketChannelInterceptor,
            webSocketRateLimitInterceptor
        )
    }

    @Bean
    @Primary
    fun taskExecutor(): Executor {
        val executor = ThreadPoolTaskExecutor()
        executor.corePoolSize = 4
        executor.maxPoolSize = 8
        executor.queueCapacity = 100
        // Back-pressure instead of failing fast when the queue is full
        executor.setRejectedExecutionHandler(ThreadPoolExecutor.CallerRunsPolicy())
        executor.setThreadNamePrefix("async-task-")
        executor.initialize()
        return executor
    }
}
