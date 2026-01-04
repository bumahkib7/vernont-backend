package com.vernont.infrastructure.cache

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.JsonNode
import org.springframework.data.redis.serializer.RedisSerializer
import org.springframework.data.redis.serializer.SerializationException

/**
 * Custom Redis serializer that handles JSON serialization with embedded type information.
 * This preserves type information without using wrapper arrays.
 */
class CustomRedisSerializer(private val objectMapper: ObjectMapper) : RedisSerializer<Any> {

    override fun serialize(value: Any?): ByteArray {
        if (value == null) {
            return ByteArray(0)
        }
        
        try {
            // Create wrapper with type information
            val wrapper = mapOf(
                "@class" to value.javaClass.name,
                "@data" to value
            )
            return objectMapper.writeValueAsBytes(wrapper)
        } catch (ex: Exception) {
            throw SerializationException("Could not serialize object", ex)
        }
    }

    override fun deserialize(bytes: ByteArray?): Any? {
        if (bytes == null || bytes.isEmpty()) {
            return null
        }

        try {
            val tree = objectMapper.readTree(bytes)
            
            // Check if it's our wrapper format
            if (tree.isObject && tree.has("@class") && tree.has("@data")) {
                val className = tree.get("@class").asText()
                val dataNode = tree.get("@data")
                
                try {
                    val clazz = Class.forName(className)
                    return objectMapper.treeToValue(dataNode, clazz)
                } catch (classEx: ClassNotFoundException) {
                    // If class not found, try to handle common types
                    return handleFallbackDeserialization(dataNode, className)
                }
            }
            
            // Fallback for legacy cached data (without type information)
            // This will return LinkedHashMap for complex objects, but won't crash
            return objectMapper.treeToValue(tree, Any::class.java)
        } catch (ex: Exception) {
            throw SerializationException("Could not deserialize object: ${ex.message}", ex)
        }
    }
    
    private fun handleFallbackDeserialization(dataNode: JsonNode, className: String): Any {
        return when {
            className.endsWith("ProductDetailView") -> {
                // Try to deserialize as ProductDetailView even if class loading fails
                try {
                    val detailViewClass = Class.forName("com.vernont.domain.affiliate.dto.ProductDetailView")
                    objectMapper.treeToValue(dataNode, detailViewClass)
                } catch (ex: Exception) {
                    objectMapper.treeToValue(dataNode, Any::class.java)
                }
            }
            className.endsWith("ProductSummaryView") -> {
                try {
                    val summaryViewClass = Class.forName("com.vernont.domain.affiliate.dto.ProductSummaryView")
                    objectMapper.treeToValue(dataNode, summaryViewClass)
                } catch (ex: Exception) {
                    objectMapper.treeToValue(dataNode, Any::class.java)
                }
            }
            else -> objectMapper.treeToValue(dataNode, Any::class.java)
        }
    }
}