package com.vernont.infrastructure.cache

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.data.redis.serializer.RedisSerializer
import org.springframework.data.redis.serializer.SerializationException

/**
 * Lenient serializer: uses a typed mapper for new writes, but on read will
 * fall back to legacy @class/@data wrapper or plain mapper to avoid crashes
 * when old cache entries are still present.
 */
class FlexibleRedisSerializer(
    private val typedMapper: ObjectMapper,
    private val plainMapper: ObjectMapper
) : RedisSerializer<Any> {

    override fun serialize(value: Any?): ByteArray {
        if (value == null) return ByteArray(0)
        return try {
            typedMapper.writeValueAsBytes(value)
        } catch (ex: Exception) {
            throw SerializationException("Could not serialize object", ex)
        }
    }

    override fun deserialize(bytes: ByteArray?): Any? {
        if (bytes == null || bytes.isEmpty()) return null
        try {
            return typedMapper.readValue(bytes, Any::class.java)
        } catch (typedEx: Exception) {
            // Try legacy @class wrapper
            try {
                val node: JsonNode = plainMapper.readTree(bytes)
                if (node.isObject && node.has("@class") && node.has("@data")) {
                    val className = node.get("@class").asText()
                    val dataNode = node.get("@data")
                    return try {
                        val clazz = Class.forName(className)
                        plainMapper.treeToValue(dataNode, clazz)
                    } catch (_: Exception) {
                        plainMapper.treeToValue(dataNode, Any::class.java)
                    }
                }
            } catch (_: Exception) {
                // ignore and try plain mapper below
            }
            return try {
                plainMapper.readValue(bytes, Any::class.java)
            } catch (ex: Exception) {
                throw SerializationException("Could not deserialize object", ex)
            }
        }
    }
}
