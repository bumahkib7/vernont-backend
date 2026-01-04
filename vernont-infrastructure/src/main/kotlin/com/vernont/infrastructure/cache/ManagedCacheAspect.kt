package com.vernont.infrastructure.cache

import com.fasterxml.jackson.databind.JavaType
import com.fasterxml.jackson.databind.ObjectMapper
import io.github.oshai.kotlinlogging.KotlinLogging
import java.lang.reflect.Type
import java.time.Duration
import org.aspectj.lang.ProceedingJoinPoint
import org.aspectj.lang.annotation.Around
import org.aspectj.lang.annotation.Aspect
import org.aspectj.lang.reflect.MethodSignature
import org.springframework.beans.factory.BeanFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.expression.BeanFactoryResolver
import org.springframework.context.expression.MethodBasedEvaluationContext
import org.springframework.core.StandardReflectionParameterNameDiscoverer
import org.springframework.core.env.Environment
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.expression.ExpressionParser
import org.springframework.expression.spel.standard.SpelExpressionParser
import org.springframework.expression.spel.support.StandardEvaluationContext
import org.springframework.stereotype.Component

@Aspect
@Component
class ManagedCacheAspect(
        private val redisTemplate: StringRedisTemplate,
        private val objectMapper: ObjectMapper,
        private val beanFactory: BeanFactory,
        private val environment: Environment,
        @Value("\${cache.prefix:affiliate}") private val cachePrefix: String,
        @Value("\${cache.default-ttl:3600}") private val defaultTtlSeconds: Long
) {

    private val log = KotlinLogging.logger {}
    private val parser: ExpressionParser = SpelExpressionParser()
    private val nameDiscoverer = StandardReflectionParameterNameDiscoverer()

    init {
        log.info {
            "ManagedCacheAspect initialized with prefix=$cachePrefix defaultTtl=$defaultTtlSeconds"
        }
    }

    @Around("@annotation(managedCache)")
    fun cacheAround(pjp: ProceedingJoinPoint, managedCache: ManagedCache): Any? {
        val method = (pjp.signature as MethodSignature).method
        val evalContext =
                MethodBasedEvaluationContext(pjp.target, method, pjp.args, nameDiscoverer).apply {
                    beanResolver = BeanFactoryResolver(beanFactory)
                }

        val cacheName: String
        val redisKey: String

        try {
            cacheName = resolveCacheName(managedCache, evalContext)
            val keyPart = resolveKey(managedCache, evalContext)
            redisKey = "$cacheName:$keyPart"
        } catch (ex: Exception) {
            log.warn(ex) { "ManagedCache: failed to resolve key/name, proceeding without cache" }
            return pjp.proceed()
        }

        val ops = redisTemplate.opsForValue()
        try {
            val cached = ops.get(redisKey)
            if (cached != null) {
                val value = deserialize(cached, method.genericReturnType)
                if (value != null) {
                    log.debug { "ManagedCache hit key=$redisKey" }
                    return value
                }
            }
        } catch (ex: Exception) {
            log.warn(ex) {
                "ManagedCache: failed to read/deserialize key=$redisKey, falling back to method execution"
            }
            // continue to proceed
        }

        val result = pjp.proceed()
        if (result == null && !managedCache.cacheNulls) {
            return result
        }
        if (!managedCache.cacheEmpty && isEmptyValue(result)) {
            return result
        }

        try {
            val ttl = resolveTtl(managedCache)
            ops.set(redisKey, serialize(result), ttl)
            log.debug { "ManagedCache stored key=$redisKey ttlSeconds=${ttl.seconds}" }
        } catch (ex: Exception) {
            log.warn(ex) { "ManagedCache: failed to write key=$redisKey, returning live result" }
        }

        return result
    }

    private fun resolveCacheName(
            managedCache: ManagedCache,
            context: StandardEvaluationContext
    ): String {
        val resolved =
                parser.parseExpression(managedCache.cacheName).getValue(context)?.toString()
                        ?: managedCache.cacheName
        return if (managedCache.prependPrefix && !resolved.startsWith("$cachePrefix:")) {
            "$cachePrefix:$resolved"
        } else {
            resolved
        }
    }

    private fun resolveKey(managedCache: ManagedCache, context: StandardEvaluationContext): String {
        val keyValue = parser.parseExpression(managedCache.key).getValue(context)
        require(keyValue != null) {
            "ManagedCache key expression '${managedCache.key}' evaluated to null"
        }
        return keyValue.toString()
    }

    private fun resolveTtl(managedCache: ManagedCache): Duration {
        val ttlSeconds =
                when {
                    managedCache.ttlSeconds > 0 -> managedCache.ttlSeconds
                    managedCache.ttlProperty.isNotBlank() ->
                            environment.getProperty(
                                    managedCache.ttlProperty,
                                    Long::class.java,
                                    defaultTtlSeconds
                            )
                    else -> defaultTtlSeconds
                }.coerceAtLeast(1)
        return Duration.ofSeconds(ttlSeconds)
    }

    private fun serialize(value: Any?): String {
        // Avoid serializing full ResponseEntity (headers contain JDK types like
        // Locale.LanguageRange)
        if (value is org.springframework.http.ResponseEntity<*>) {
            return objectMapper.writeValueAsString(value.body)
        }
        return objectMapper.writeValueAsString(value)
    }

    private fun isEmptyValue(value: Any?): Boolean {
        if (value == null) return true
        if (value is org.springframework.http.ResponseEntity<*>) {
            return isEmptyValue(value.body)
        }
        return when (value) {
            is String -> value.isBlank()
            is Collection<*> -> value.isEmpty()
            is Map<*, *> -> value.isEmpty()
            is Array<*> -> value.isEmpty()
            else -> {
                val getter =
                        value::class.java.methods.firstOrNull {
                            it.name == "getItems" && it.parameterCount == 0
                        }
                                ?: return false
                val items = runCatching { getter.invoke(value) }.getOrNull()
                when (items) {
                    is Collection<*> -> items.isEmpty()
                    is Map<*, *> -> items.isEmpty()
                    is Array<*> -> items.isEmpty()
                    else -> false
                }
            }
        }
    }

    private fun toJavaType(returnType: Type): JavaType =
            objectMapper.typeFactory.constructType(returnType)

    private fun deserialize(payload: String, returnType: Type): Any? {
        // If method returns ResponseEntity<T>, deserialize as T and wrap in ResponseEntity.ok
        if (returnType is java.lang.reflect.ParameterizedType &&
                        (returnType.rawType as? Class<*>) ==
                                org.springframework.http.ResponseEntity::class.java
        ) {
            val bodyType: Type = returnType.actualTypeArguments.firstOrNull() ?: Any::class.java
            val bodyJavaType = toJavaType(bodyType)
            return try {
                val bodyValue: Any = objectMapper.readValue(payload, bodyJavaType) ?: return null
                org.springframework.http.ResponseEntity.ok(bodyValue)
            } catch (ex: Exception) {
                log.warn(ex) {
                    "ManagedCache: deserialize fallback for ResponseEntity body type=$bodyType"
                }
                null
            }
        }

        val javaType = toJavaType(returnType)
        return try {
            // objectMapper.readValue can return a List of LinkedHashMaps for generic types.
            // A second pass with convertValue is required to shape it into the target type.
            val value: Any = objectMapper.readValue(payload, javaType) ?: return null
            objectMapper.convertValue<Any?>(value, javaType)
        } catch (ex: Exception) {
            log.warn(ex) { "ManagedCache: deserialize fallback for type=$returnType" }
            null
        }
    }
}
