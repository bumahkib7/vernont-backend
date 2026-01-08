package com.vernont.workflow.common

import kotlin.reflect.KMutableProperty0

/**
 * DSL for handling partial updates elegantly.
 * Allows clean, declarative syntax for applying nullable updates to entities.
 *
 * Usage:
 * ```kotlin
 * product.applyUpdates {
 *     input.title ifPresent { title = it }
 *     input.description ifPresent { description = it }
 *     input.status ifPresent { status = parseStatus(it) }
 * }
 * ```
 */
@DslMarker
annotation class PartialUpdateDsl

@PartialUpdateDsl
class PartialUpdateContext<T>(val entity: T) {

    /**
     * Apply value if not null
     */
    infix fun <V> V?.ifPresent(block: T.(V) -> Unit) {
        if (this != null) {
            entity.block(this)
        }
    }

    /**
     * Apply value if not null, with validation
     */
    inline fun <V> V?.ifPresentValidated(
        crossinline validator: (V) -> Unit,
        crossinline block: T.(V) -> Unit
    ) {
        if (this != null) {
            validator(this)
            entity.block(this)
        }
    }

    /**
     * Apply value to a property directly
     */
    infix fun <V> V?.into(property: KMutableProperty0<V>) {
        if (this != null) {
            property.set(this)
        }
    }

    /**
     * Apply transformed value if not null
     */
    inline fun <V, R> V?.ifPresentTransform(
        crossinline transform: (V) -> R,
        crossinline block: T.(R) -> Unit
    ) {
        if (this != null) {
            entity.block(transform(this))
        }
    }

    /**
     * Apply value or throw if validation fails
     */
    inline fun <V> V?.ifPresentOrThrow(
        crossinline validator: (V) -> Boolean,
        crossinline errorMessage: (V) -> String,
        crossinline block: T.(V) -> Unit
    ) {
        if (this != null) {
            if (!validator(this)) {
                throw IllegalArgumentException(errorMessage(this))
            }
            entity.block(this)
        }
    }
}

/**
 * Extension function to start a partial update block
 */
inline fun <T> T.applyUpdates(block: PartialUpdateContext<T>.() -> Unit): T {
    PartialUpdateContext(this).block()
    return this
}

/**
 * Batch update helper - applies updates to multiple entities
 */
inline fun <T, I> List<T>.applyBatchUpdates(
    inputs: List<I>,
    crossinline block: PartialUpdateContext<T>.(I) -> Unit
): List<T> {
    require(this.size == inputs.size) { "Entity and input list sizes must match" }
    return this.mapIndexed { index, entity ->
        entity.applyUpdates { block(inputs[index]) }
    }
}

/**
 * Result wrapper for update operations with validation
 */
sealed class UpdateResult<T> {
    data class Success<T>(val entity: T) : UpdateResult<T>()
    data class ValidationError<T>(val field: String, val message: String) : UpdateResult<T>()
    data class NotFound<T>(val entityType: String, val id: String) : UpdateResult<T>()

    fun getOrThrow(): T = when (this) {
        is Success -> entity
        is ValidationError -> throw IllegalArgumentException("$field: $message")
        is NotFound -> throw IllegalArgumentException("$entityType not found: $id")
    }

    inline fun <R> map(transform: (T) -> R): UpdateResult<R> = when (this) {
        is Success -> Success(transform(entity))
        is ValidationError -> ValidationError(field, message)
        is NotFound -> NotFound(entityType, id)
    }
}

/**
 * Field update descriptor for more complex scenarios
 */
data class FieldUpdate<T, V>(
    val value: V?,
    val fieldName: String,
    val validator: ((V) -> Boolean)? = null,
    val errorMessage: String? = null,
    val transform: ((V) -> Any)? = null
) {
    fun applyTo(entity: T, setter: T.(V) -> Unit) {
        value?.let { v ->
            validator?.let { validate ->
                if (!validate(v)) {
                    throw IllegalArgumentException(errorMessage ?: "Invalid value for $fieldName: $v")
                }
            }
            entity.setter(v)
        }
    }
}

/**
 * Builder for creating field updates with validation
 */
class FieldUpdateBuilder<T, V>(private val value: V?, private val fieldName: String) {
    private var validator: ((V) -> Boolean)? = null
    private var errorMessage: String? = null

    fun validate(predicate: (V) -> Boolean): FieldUpdateBuilder<T, V> {
        this.validator = predicate
        return this
    }

    fun errorMessage(message: String): FieldUpdateBuilder<T, V> {
        this.errorMessage = message
        return this
    }

    fun build(): FieldUpdate<T, V> = FieldUpdate(value, fieldName, validator, errorMessage)
}

/**
 * Create a field update builder
 */
fun <T, V> field(value: V?, name: String): FieldUpdateBuilder<T, V> = FieldUpdateBuilder(value, name)

/**
 * Extension for nullable string to non-blank validation
 */
fun String?.ifNotBlank(block: (String) -> Unit) {
    if (!this.isNullOrBlank()) {
        block(this)
    }
}

/**
 * Extension for nullable collections
 */
fun <T> List<T>?.ifNotEmpty(block: (List<T>) -> Unit) {
    if (!this.isNullOrEmpty()) {
        block(this)
    }
}

/**
 * Top-level extension for nullable values - useful outside of applyUpdates blocks.
 * Use this when you need to conditionally execute code based on a nullable value.
 *
 * Usage:
 * ```kotlin
 * input.collectionId ifPresent { collectionId ->
 *     product.collection = resolveCollection(collectionId)
 * }
 * ```
 */
infix fun <V> V?.ifPresent(block: (V) -> Unit) {
    if (this != null) {
        block(this)
    }
}
