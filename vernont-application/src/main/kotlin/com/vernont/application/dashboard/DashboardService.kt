package com.vernont.application.dashboard

import com.vernont.domain.audit.AuditLog
import com.vernont.domain.order.FulfillmentStatus
import com.vernont.domain.order.Order
import com.vernont.domain.order.OrderStatus
import com.vernont.domain.order.PaymentStatus
import com.vernont.domain.product.ProductStatus
import com.vernont.repository.AuditLogRepository
import com.vernont.repository.customer.CustomerRepository
import com.vernont.repository.inventory.InventoryItemRepository
import com.vernont.repository.order.OrderRepository
import com.vernont.repository.product.ProductRepository
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit

private val logger = KotlinLogging.logger {}

@Service
@Transactional(readOnly = true)
class DashboardService(
    private val orderRepository: OrderRepository,
    private val customerRepository: CustomerRepository,
    private val productRepository: ProductRepository,
    private val inventoryItemRepository: InventoryItemRepository,
    private val auditLogRepository: AuditLogRepository
) {

    /**
     * Get comprehensive dashboard statistics
     */
    fun getDashboardStats(): DashboardStats {
        logger.debug { "Fetching dashboard stats" }

        val now = Instant.now()
        val todayStart = LocalDate.now().atStartOfDay().toInstant(ZoneOffset.UTC)
        val yesterdayStart = todayStart.minus(1, ChronoUnit.DAYS)
        val weekAgo = todayStart.minus(7, ChronoUnit.DAYS)

        return DashboardStats(
            revenue = getRevenueStats(todayStart, yesterdayStart, now),
            orders = getOrderStats(todayStart, now),
            customers = getCustomerOverview(weekAgo),
            products = getProductOverview(),
            recentOrders = getRecentOrders(5),
            activityFeed = getActivityFeed(10)
        )
    }

    /**
     * Get revenue statistics comparing today vs yesterday
     */
    private fun getRevenueStats(todayStart: Instant, yesterdayStart: Instant, now: Instant): RevenueStats {
        val todayRevenue = orderRepository.sumTotalByStatusAndCreatedAtBetween(
            OrderStatus.COMPLETED,
            todayStart,
            now
        ) ?: BigDecimal.ZERO

        val yesterdayRevenue = orderRepository.sumTotalByStatusAndCreatedAtBetween(
            OrderStatus.COMPLETED,
            yesterdayStart,
            todayStart
        ) ?: BigDecimal.ZERO

        val changePercent = if (yesterdayRevenue > BigDecimal.ZERO) {
            todayRevenue.subtract(yesterdayRevenue)
                .divide(yesterdayRevenue, 4, RoundingMode.HALF_UP)
                .multiply(BigDecimal(100))
                .setScale(1, RoundingMode.HALF_UP)
                .toDouble()
        } else if (todayRevenue > BigDecimal.ZERO) {
            100.0
        } else {
            0.0
        }

        return RevenueStats(
            today = todayRevenue,
            yesterday = yesterdayRevenue,
            changePercent = changePercent,
            trend = if (changePercent >= 0) "up" else "down"
        )
    }

    /**
     * Get order statistics
     */
    private fun getOrderStats(todayStart: Instant, now: Instant): OrderStats {
        val todayOrders = orderRepository.findByCreatedAtBetween(todayStart, now)
        val pendingOrders = orderRepository.countByPaymentStatus(PaymentStatus.NOT_PAID) +
            orderRepository.countByPaymentStatus(PaymentStatus.AWAITING)
        val processingOrders = orderRepository.countByFulfillmentStatus(FulfillmentStatus.NOT_FULFILLED)

        // Orders requiring action: pending status with awaiting payment
        val requiresAction = orderRepository.findByStatusAndPaymentStatus(
            OrderStatus.PENDING,
            PaymentStatus.AWAITING
        ).size + orderRepository.findByStatusAndPaymentStatus(
            OrderStatus.PENDING,
            PaymentStatus.REQUIRES_ACTION
        ).size

        return OrderStats(
            today = todayOrders.size,
            pending = pendingOrders.toInt(),
            processing = processingOrders.toInt(),
            requiresAction = requiresAction
        )
    }

    /**
     * Get customer overview
     */
    private fun getCustomerOverview(weekAgo: Instant): CustomerOverview {
        val totalCustomers = customerRepository.count()
        val newThisWeek = customerRepository.countByCreatedAtAfter(weekAgo)

        return CustomerOverview(
            total = totalCustomers.toInt(),
            newThisWeek = newThisWeek.toInt()
        )
    }

    /**
     * Get product overview including low stock count
     */
    private fun getProductOverview(): ProductOverview {
        val totalProducts = productRepository.countByStatus(ProductStatus.PUBLISHED)
        val lowStockItems = inventoryItemRepository.findLowStockItems(10)

        return ProductOverview(
            total = totalProducts.toInt(),
            lowStock = lowStockItems.size
        )
    }

    /**
     * Get recent orders with summary info
     */
    fun getRecentOrders(limit: Int = 10): List<RecentOrderSummary> {
        val pageable = PageRequest.of(0, limit, Sort.by(Sort.Direction.DESC, "createdAt"))
        val orders = orderRepository.findAll(pageable)

        return orders.content
            .filter { it.deletedAt == null }
            .map { order -> mapToRecentOrderSummary(order) }
    }

    private fun mapToRecentOrderSummary(order: Order): RecentOrderSummary {
        val items = order.items.take(3).map { item ->
            OrderItemSummary(
                name = item.title ?: "Unknown Product",
                image = item.thumbnail
            )
        }

        return RecentOrderSummary(
            id = order.id,
            displayId = "#${order.displayId}",
            customerName = "${order.shippingAddress?.firstName ?: ""} ${order.shippingAddress?.lastName ?: ""}".trim()
                .ifEmpty { order.email ?: "Guest" },
            customerEmail = order.email,
            date = order.createdAt,
            total = order.total,
            status = order.status.name.lowercase(),
            paymentStatus = order.paymentStatus.name.lowercase(),
            fulfillmentStatus = order.fulfillmentStatus.name.lowercase(),
            itemCount = order.items.size,
            items = items
        )
    }

    /**
     * Get activity feed from audit logs
     */
    fun getActivityFeed(limit: Int = 20): List<ActivityItem> {
        val pageable = PageRequest.of(0, limit, Sort.by(Sort.Direction.DESC, "timestamp"))
        val auditLogs = auditLogRepository.findAll(pageable)

        return auditLogs.content.map { log -> mapToActivityItem(log) }
    }

    private fun mapToActivityItem(log: AuditLog): ActivityItem {
        val message = buildActivityMessage(log)
        val type = mapAuditActionToActivityType(log.action.name, log.entityType)

        return ActivityItem(
            id = log.id.toString(),
            type = type,
            message = message,
            entityType = log.entityType,
            entityId = log.entityId,
            timestamp = log.timestamp,
            userId = log.userId,
            userName = log.userName
        )
    }

    private fun buildActivityMessage(log: AuditLog): String {
        val action = when (log.action.name) {
            "CREATE" -> "created"
            "UPDATE" -> "updated"
            "DELETE" -> "deleted"
            "LOGIN" -> "logged in"
            "LOGOUT" -> "logged out"
            else -> log.action.name.lowercase()
        }

        val entity = log.entityType?.lowercase()?.replace("_", " ") ?: "item"
        val actor = log.userName ?: "System"

        return when {
            log.action.name == "LOGIN" || log.action.name == "LOGOUT" -> "$actor $action"
            log.entityId != null -> "$actor $action $entity #${log.entityId.takeLast(8)}"
            else -> "$actor $action $entity"
        }
    }

    private fun mapAuditActionToActivityType(action: String, entityType: String?): String {
        return when {
            entityType?.contains("ORDER", ignoreCase = true) == true -> "order"
            entityType?.contains("CUSTOMER", ignoreCase = true) == true -> "customer"
            entityType?.contains("PRODUCT", ignoreCase = true) == true -> "product"
            entityType?.contains("INVENTORY", ignoreCase = true) == true -> "stock"
            action == "LOGIN" || action == "LOGOUT" -> "auth"
            else -> "system"
        }
    }

    /**
     * Get KPIs for analytics page with period selection
     */
    fun getKpis(period: String): KpiData {
        val now = Instant.now()
        val (startDate, previousStartDate) = when (period) {
            "7d" -> {
                val start = now.minus(7, ChronoUnit.DAYS)
                val prevStart = start.minus(7, ChronoUnit.DAYS)
                start to prevStart
            }
            "30d" -> {
                val start = now.minus(30, ChronoUnit.DAYS)
                val prevStart = start.minus(30, ChronoUnit.DAYS)
                start to prevStart
            }
            "90d" -> {
                val start = now.minus(90, ChronoUnit.DAYS)
                val prevStart = start.minus(90, ChronoUnit.DAYS)
                start to prevStart
            }
            "12m" -> {
                val start = now.minus(365, ChronoUnit.DAYS)
                val prevStart = start.minus(365, ChronoUnit.DAYS)
                start to prevStart
            }
            else -> {
                val start = now.minus(30, ChronoUnit.DAYS)
                val prevStart = start.minus(30, ChronoUnit.DAYS)
                start to prevStart
            }
        }

        // Current period
        val currentRevenue = orderRepository.sumTotalByStatusAndCreatedAtBetween(
            OrderStatus.COMPLETED, startDate, now
        ) ?: BigDecimal.ZERO

        val currentOrders = orderRepository.findByCreatedAtBetween(startDate, now).size

        val currentCustomers = customerRepository.countByCreatedAtAfter(startDate)

        // Previous period for comparison
        val previousRevenue = orderRepository.sumTotalByStatusAndCreatedAtBetween(
            OrderStatus.COMPLETED, previousStartDate, startDate
        ) ?: BigDecimal.ZERO

        val previousOrders = orderRepository.findByCreatedAtBetween(previousStartDate, startDate).size

        val previousCustomers = customerRepository.countByCreatedAtBetween(previousStartDate, startDate)

        // Calculate changes
        fun calculateChange(current: BigDecimal, previous: BigDecimal): Double {
            return if (previous > BigDecimal.ZERO) {
                current.subtract(previous)
                    .divide(previous, 4, RoundingMode.HALF_UP)
                    .multiply(BigDecimal(100))
                    .setScale(1, RoundingMode.HALF_UP)
                    .toDouble()
            } else if (current > BigDecimal.ZERO) 100.0 else 0.0
        }

        fun calculateChangeInt(current: Int, previous: Int): Double {
            return if (previous > 0) {
                ((current - previous).toDouble() / previous * 100).let {
                    (it * 10).toInt() / 10.0
                }
            } else if (current > 0) 100.0 else 0.0
        }

        val revenueChange = calculateChange(currentRevenue, previousRevenue)
        val ordersChange = calculateChangeInt(currentOrders, previousOrders)
        val customersChange = calculateChangeInt(currentCustomers.toInt(), previousCustomers.toInt())

        // Conversion rate (orders / unique visitors) - placeholder since we don't track visitors
        val conversionRate = if (currentCustomers > 0) {
            (currentOrders.toDouble() / currentCustomers * 100).let {
                (it * 100).toInt() / 100.0
            }
        } else 0.0

        return KpiData(
            totalRevenue = KpiItem(
                value = currentRevenue,
                change = revenueChange,
                trend = if (revenueChange >= 0) "up" else "down"
            ),
            totalOrders = KpiItem(
                value = BigDecimal(currentOrders),
                change = ordersChange,
                trend = if (ordersChange >= 0) "up" else "down"
            ),
            newCustomers = KpiItem(
                value = BigDecimal(currentCustomers),
                change = customersChange,
                trend = if (customersChange >= 0) "up" else "down"
            ),
            conversionRate = KpiItem(
                value = BigDecimal(conversionRate).setScale(2, RoundingMode.HALF_UP),
                change = 0.0, // Would need visitor tracking for accurate comparison
                trend = "neutral"
            ),
            period = period
        )
    }

    /**
     * Get comprehensive analytics data for a period
     */
    fun getAnalyticsData(period: String): AnalyticsData {
        logger.debug { "Fetching analytics data for period: $period" }

        val now = Instant.now()
        val startDate = when (period) {
            "7d" -> now.minus(7, ChronoUnit.DAYS)
            "30d" -> now.minus(30, ChronoUnit.DAYS)
            "90d" -> now.minus(90, ChronoUnit.DAYS)
            "12m" -> now.minus(365, ChronoUnit.DAYS)
            else -> now.minus(30, ChronoUnit.DAYS)
        }

        return AnalyticsData(
            salesOverTime = getSalesOverTime(startDate, now, period),
            topProducts = getTopProducts(startDate, now, 5),
            topCategories = getTopCategories(startDate, now, 5)
        )
    }

    /**
     * Get sales data over time for charting
     */
    private fun getSalesOverTime(startDate: Instant, endDate: Instant, period: String): List<SalesDataPoint> {
        val orders = orderRepository.findByCreatedAtBetween(startDate, endDate)
            .filter { it.deletedAt == null && it.status == OrderStatus.COMPLETED }

        val dateFormatter = java.time.format.DateTimeFormatter.ofPattern("MMM d")
            .withZone(ZoneOffset.UTC)

        // Group by day and sum totals
        val salesByDay = orders.groupBy { order ->
            LocalDate.ofInstant(order.createdAt, ZoneOffset.UTC)
        }.mapValues { (_, dayOrders) ->
            dayOrders.fold(BigDecimal.ZERO) { acc, order -> acc + order.total }
        }

        // Generate date range
        val dates = generateSequence(LocalDate.ofInstant(startDate, ZoneOffset.UTC)) { it.plusDays(1) }
            .takeWhile { !it.isAfter(LocalDate.ofInstant(endDate, ZoneOffset.UTC)) }
            .toList()

        // Sample dates based on period to avoid too many data points
        val sampledDates = when (period) {
            "7d" -> dates // All days
            "30d" -> dates.filterIndexed { index, _ -> index % 2 == 0 } // Every other day
            "90d" -> dates.filterIndexed { index, _ -> index % 7 == 0 } // Weekly
            "12m" -> dates.filterIndexed { index, _ -> index % 14 == 0 } // Bi-weekly
            else -> dates.filterIndexed { index, _ -> index % 2 == 0 }
        }

        return sampledDates.map { date ->
            SalesDataPoint(
                date = dateFormatter.format(date.atStartOfDay(ZoneOffset.UTC)),
                revenue = salesByDay[date] ?: BigDecimal.ZERO
            )
        }
    }

    /**
     * Get top performing products by revenue
     */
    private fun getTopProducts(startDate: Instant, endDate: Instant, limit: Int): List<TopProductSummary> {
        val orders = orderRepository.findByCreatedAtBetween(startDate, endDate)
            .filter { it.deletedAt == null && it.status == OrderStatus.COMPLETED }

        // Aggregate line items by product title
        val productStats = mutableMapOf<String, Pair<BigDecimal, Int>>()

        orders.forEach { order ->
            order.items.forEach { item ->
                val productName = item.title
                val current = productStats[productName] ?: (BigDecimal.ZERO to 0)
                productStats[productName] = (current.first + item.total) to (current.second + 1)
            }
        }

        // Sort by revenue and take top N
        return productStats.entries
            .sortedByDescending { it.value.first }
            .take(limit)
            .map { (name, stats) ->
                TopProductSummary(
                    name = name,
                    revenue = stats.first,
                    orders = stats.second,
                    growth = 0.0 // Would need previous period comparison for actual growth
                )
            }
    }

    /**
     * Get top categories by sales
     * Since line items don't have category, we group by product type/collection from title patterns
     */
    private fun getTopCategories(startDate: Instant, endDate: Instant, limit: Int): List<TopCategorySummary> {
        val orders = orderRepository.findByCreatedAtBetween(startDate, endDate)
            .filter { it.deletedAt == null && it.status == OrderStatus.COMPLETED }

        // Try to categorize products based on keywords in title (perfume store)
        val categoryKeywords = mapOf(
            "Eau de Parfum" to listOf("edp", "eau de parfum", "parfum"),
            "Eau de Toilette" to listOf("edt", "eau de toilette", "toilette"),
            "Cologne" to listOf("cologne", "edc", "eau de cologne"),
            "Body Care" to listOf("body", "lotion", "cream", "oil", "mist"),
            "Gift Sets" to listOf("set", "gift", "collection", "coffret")
        )

        val categorySales = mutableMapOf<String, BigDecimal>()

        orders.forEach { order ->
            order.items.forEach { item ->
                val titleLower = item.title.lowercase()
                val category = categoryKeywords.entries.find { (_, keywords) ->
                    keywords.any { titleLower.contains(it) }
                }?.key ?: "Other"

                categorySales[category] = (categorySales[category] ?: BigDecimal.ZERO) + item.total
            }
        }

        return categorySales.entries
            .sortedByDescending { it.value }
            .take(limit)
            .map { (category, sales) ->
                TopCategorySummary(
                    category = category,
                    sales = sales
                )
            }
    }
}

// Data classes for dashboard responses
data class DashboardStats(
    val revenue: RevenueStats,
    val orders: OrderStats,
    val customers: CustomerOverview,
    val products: ProductOverview,
    val recentOrders: List<RecentOrderSummary>,
    val activityFeed: List<ActivityItem>
)

data class RevenueStats(
    val today: BigDecimal,
    val yesterday: BigDecimal,
    val changePercent: Double,
    val trend: String
)

data class OrderStats(
    val today: Int,
    val pending: Int,
    val processing: Int,
    val requiresAction: Int
)

data class CustomerOverview(
    val total: Int,
    val newThisWeek: Int
)

data class ProductOverview(
    val total: Int,
    val lowStock: Int
)

data class RecentOrderSummary(
    val id: String,
    val displayId: String,
    val customerName: String,
    val customerEmail: String?,
    val date: Instant,
    val total: BigDecimal,
    val status: String,
    val paymentStatus: String,
    val fulfillmentStatus: String,
    val itemCount: Int,
    val items: List<OrderItemSummary>
)

data class OrderItemSummary(
    val name: String,
    val image: String?
)

data class ActivityItem(
    val id: String,
    val type: String,
    val message: String,
    val entityType: String?,
    val entityId: String?,
    val timestamp: Instant,
    val userId: String?,
    val userName: String?
)

data class KpiData(
    val totalRevenue: KpiItem,
    val totalOrders: KpiItem,
    val newCustomers: KpiItem,
    val conversionRate: KpiItem,
    val period: String
)

data class KpiItem(
    val value: BigDecimal,
    val change: Double,
    val trend: String
)

// Analytics data classes
data class SalesDataPoint(
    val date: String,
    val revenue: BigDecimal
)

data class TopProductSummary(
    val name: String,
    val revenue: BigDecimal,
    val orders: Int,
    val growth: Double
)

data class TopCategorySummary(
    val category: String,
    val sales: BigDecimal
)

data class AnalyticsData(
    val salesOverTime: List<SalesDataPoint>,
    val topProducts: List<TopProductSummary>,
    val topCategories: List<TopCategorySummary>
)
