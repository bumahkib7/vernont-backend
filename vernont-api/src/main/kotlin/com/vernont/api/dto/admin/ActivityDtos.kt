package com.vernont.api.dto.admin

import com.vernont.domain.audit.AuditAction
import com.vernont.domain.audit.AuditLog
import java.time.Instant

// ============================================================================
// Activity Response DTOs
// ============================================================================

data class ActivityListResponse(
    val items: List<ActivityDto>,
    val count: Int,
    val hasMore: Boolean
)

data class ActivityDto(
    val id: String,
    val type: String,
    val message: String,
    val entityType: String?,
    val entityId: String?,
    val timestamp: Instant,
    val userId: String?,
    val userName: String?
) {
    companion object {
        fun from(auditLog: AuditLog): ActivityDto {
            val entityConfig = ENTITY_CONFIG[auditLog.entityType]

            return ActivityDto(
                id = auditLog.id?.toString() ?: "",
                type = formatActivityType(auditLog.entityType, auditLog.action),
                message = formatActivityMessage(auditLog, entityConfig),
                entityType = entityConfig?.displayName?.lowercase() ?: auditLog.entityType.lowercase(),
                entityId = auditLog.entityId,
                timestamp = auditLog.timestamp,
                userId = auditLog.userId,
                userName = auditLog.userName
            )
        }

        private fun formatActivityType(entityType: String, action: AuditAction): String {
            val config = ENTITY_CONFIG[entityType]
            val entity = config?.displayName?.lowercase()?.replace(" ", "_") ?: entityType.lowercase()
            val actionStr = action.name.lowercase()
            return "${entity}_${actionStr}"
        }

        private fun formatActivityMessage(auditLog: AuditLog, config: EntityDisplayConfig?): String {
            val action = auditLog.action
            val userName = auditLog.userName?.takeIf { it != "SYSTEM" && it.isNotBlank() }

            // Use custom message generator if available
            config?.messageGenerator?.let { generator ->
                return generator(auditLog, userName)
            }

            // Default message format
            val entityName = config?.displayName ?: auditLog.entityType.splitCamelCase()
            val actionVerb = getActionVerb(action)
            val shortId = auditLog.entityId.takeIf { it.isNotBlank() }?.take(8) ?: ""

            return if (userName != null) {
                "$userName $actionVerb ${entityName.lowercase()}${if (shortId.isNotEmpty()) " #$shortId" else ""}"
            } else {
                "${entityName.capitalize()} $actionVerb${if (shortId.isNotEmpty()) " #$shortId" else ""}"
            }
        }

        private fun getActionVerb(action: AuditAction): String = when (action) {
            AuditAction.CREATE -> "created"
            AuditAction.UPDATE -> "updated"
            AuditAction.DELETE -> "deleted"
            AuditAction.READ -> "viewed"
            AuditAction.LOGIN -> "logged in"
            AuditAction.LOGOUT -> "logged out"
            AuditAction.LOGIN_FAILED -> "failed to login"
            AuditAction.PERMISSION_DENIED -> "was denied access"
        }

        private fun String.splitCamelCase(): String {
            return this.replace(Regex("([a-z])([A-Z])"), "$1 $2")
        }

        private fun String.capitalize(): String {
            return this.replaceFirstChar { it.uppercase() }
        }
    }
}

// ============================================================================
// Entity Display Configuration
// ============================================================================

data class EntityDisplayConfig(
    val displayName: String,
    val icon: String? = null,
    val messageGenerator: ((AuditLog, String?) -> String)? = null
)

/**
 * Configuration for user-facing entities that should appear in activity feed.
 * Key = JPA entity class name (e.g., "Order", "Product")
 */
val ENTITY_CONFIG: Map<String, EntityDisplayConfig> = mapOf(
    // Orders
    "Order" to EntityDisplayConfig(
        displayName = "Order",
        icon = "shopping-bag",
        messageGenerator = { log, user ->
            val shortId = log.entityId.take(8)
            when (log.action) {
                AuditAction.CREATE -> "New order #$shortId placed"
                AuditAction.UPDATE -> "Order #$shortId updated"
                AuditAction.DELETE -> "Order #$shortId cancelled"
                else -> "Order #$shortId ${log.action.name.lowercase()}"
            }
        }
    ),

    // Products
    "Product" to EntityDisplayConfig(
        displayName = "Product",
        icon = "package",
        messageGenerator = { log, user ->
            val shortId = log.entityId.take(8)
            val prefix = user?.let { "$it " } ?: ""
            when (log.action) {
                AuditAction.CREATE -> "${prefix}added a new product"
                AuditAction.UPDATE -> "${prefix}updated product #$shortId"
                AuditAction.DELETE -> "${prefix}removed product #$shortId"
                else -> "${prefix}modified product #$shortId"
            }
        }
    ),
    "ProductVariant" to EntityDisplayConfig(
        displayName = "Product variant",
        icon = "package",
        messageGenerator = { log, user ->
            val prefix = user?.let { "$it " } ?: ""
            when (log.action) {
                AuditAction.CREATE -> "${prefix}added a product variant"
                AuditAction.UPDATE -> "${prefix}updated product variant"
                AuditAction.DELETE -> "${prefix}removed product variant"
                else -> "${prefix}modified product variant"
            }
        }
    ),

    // Customers
    "Customer" to EntityDisplayConfig(
        displayName = "Customer",
        icon = "user",
        messageGenerator = { log, user ->
            when (log.action) {
                AuditAction.CREATE -> "New customer registered"
                AuditAction.UPDATE -> "Customer profile updated"
                AuditAction.DELETE -> "Customer account removed"
                else -> "Customer ${log.action.name.lowercase()}"
            }
        }
    ),

    // Payments
    "Payment" to EntityDisplayConfig(
        displayName = "Payment",
        icon = "credit-card",
        messageGenerator = { log, user ->
            val shortId = log.entityId.take(8)
            when (log.action) {
                AuditAction.CREATE -> "Payment #$shortId received"
                AuditAction.UPDATE -> "Payment #$shortId updated"
                AuditAction.DELETE -> "Payment #$shortId voided"
                else -> "Payment #$shortId ${log.action.name.lowercase()}"
            }
        }
    ),
    "Refund" to EntityDisplayConfig(
        displayName = "Refund",
        icon = "refresh-cw",
        messageGenerator = { log, user ->
            val shortId = log.entityId.take(8)
            when (log.action) {
                AuditAction.CREATE -> "Refund #$shortId issued"
                AuditAction.UPDATE -> "Refund #$shortId updated"
                else -> "Refund #$shortId ${log.action.name.lowercase()}"
            }
        }
    ),

    // Fulfillment
    "Fulfillment" to EntityDisplayConfig(
        displayName = "Shipment",
        icon = "truck",
        messageGenerator = { log, user ->
            val shortId = log.entityId.take(8)
            when (log.action) {
                AuditAction.CREATE -> "Shipment #$shortId created"
                AuditAction.UPDATE -> "Shipment #$shortId updated"
                AuditAction.DELETE -> "Shipment #$shortId cancelled"
                else -> "Shipment #$shortId ${log.action.name.lowercase()}"
            }
        }
    ),

    // Inventory
    "InventoryItem" to EntityDisplayConfig(
        displayName = "Inventory",
        icon = "archive",
        messageGenerator = { log, user ->
            val prefix = user?.let { "$it " } ?: ""
            when (log.action) {
                AuditAction.CREATE -> "${prefix}added inventory item"
                AuditAction.UPDATE -> "${prefix}adjusted inventory"
                AuditAction.DELETE -> "${prefix}removed inventory item"
                else -> "${prefix}modified inventory"
            }
        }
    ),
    "InventoryLevel" to EntityDisplayConfig(
        displayName = "Stock level",
        icon = "archive",
        messageGenerator = { log, user ->
            when (log.action) {
                AuditAction.UPDATE -> "Stock level adjusted"
                else -> "Stock ${log.action.name.lowercase()}"
            }
        }
    ),

    // Returns
    "Return" to EntityDisplayConfig(
        displayName = "Return",
        icon = "corner-down-left",
        messageGenerator = { log, user ->
            val shortId = log.entityId.take(8)
            when (log.action) {
                AuditAction.CREATE -> "Return #$shortId requested"
                AuditAction.UPDATE -> "Return #$shortId updated"
                AuditAction.DELETE -> "Return #$shortId cancelled"
                else -> "Return #$shortId ${log.action.name.lowercase()}"
            }
        }
    ),

    // Gift Cards
    "GiftCard" to EntityDisplayConfig(
        displayName = "Gift card",
        icon = "gift",
        messageGenerator = { log, user ->
            val prefix = user?.let { "$it " } ?: ""
            when (log.action) {
                AuditAction.CREATE -> "${prefix}issued a gift card"
                AuditAction.UPDATE -> "${prefix}updated gift card"
                AuditAction.DELETE -> "${prefix}disabled gift card"
                else -> "${prefix}modified gift card"
            }
        }
    ),

    // Discounts & Promotions
    "Discount" to EntityDisplayConfig(
        displayName = "Discount",
        icon = "percent",
        messageGenerator = { log, user ->
            val prefix = user?.let { "$it " } ?: ""
            when (log.action) {
                AuditAction.CREATE -> "${prefix}created a discount"
                AuditAction.UPDATE -> "${prefix}updated discount"
                AuditAction.DELETE -> "${prefix}removed discount"
                else -> "${prefix}modified discount"
            }
        }
    ),
    "Promotion" to EntityDisplayConfig(
        displayName = "Promotion",
        icon = "tag",
        messageGenerator = { log, user ->
            val prefix = user?.let { "$it " } ?: ""
            when (log.action) {
                AuditAction.CREATE -> "${prefix}created a promotion"
                AuditAction.UPDATE -> "${prefix}updated promotion"
                AuditAction.DELETE -> "${prefix}ended promotion"
                else -> "${prefix}modified promotion"
            }
        }
    )
)

/**
 * Set of entity types that should be displayed in the activity feed.
 * Only entities in ENTITY_CONFIG will be shown.
 */
val ALLOWED_ENTITY_TYPES: Set<String> = ENTITY_CONFIG.keys

/**
 * Check if an audit log entry should be displayed in the activity feed.
 */
fun isBusinessEvent(auditLog: AuditLog): Boolean {
    // Must be an allowed entity type
    if (auditLog.entityType !in ALLOWED_ENTITY_TYPES) return false

    // Must be a business action (CREATE, UPDATE, DELETE)
    if (auditLog.action !in setOf(AuditAction.CREATE, AuditAction.UPDATE, AuditAction.DELETE)) return false

    return true
}
