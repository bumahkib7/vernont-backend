package com.vernont.api.controller.admin

import com.vernont.application.dashboard.ActivityItem
import com.vernont.application.dashboard.AnalyticsData
import com.vernont.application.dashboard.DashboardService
import com.vernont.application.dashboard.DashboardStats
import com.vernont.application.dashboard.KpiData
import com.vernont.application.dashboard.RecentOrderSummary
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/admin/dashboard")
@Tag(name = "Admin Dashboard", description = "Dashboard statistics and activity endpoints")
class AdminDashboardController(
    private val dashboardService: DashboardService
) {

    /**
     * Get comprehensive dashboard statistics
     * GET /admin/dashboard/stats
     */
    @GetMapping("/stats")
    @Operation(summary = "Get dashboard statistics", description = "Returns revenue, orders, customers, products stats, recent orders, and activity feed")
    fun getDashboardStats(): ResponseEntity<DashboardStats> {
        val stats = dashboardService.getDashboardStats()
        return ResponseEntity.ok(stats)
    }

    /**
     * Get recent orders
     * GET /admin/dashboard/recent-orders
     */
    @GetMapping("/recent-orders")
    @Operation(summary = "Get recent orders", description = "Returns the most recent orders with summary info")
    fun getRecentOrders(
        @RequestParam(required = false, defaultValue = "10") limit: Int
    ): ResponseEntity<List<RecentOrderSummary>> {
        val orders = dashboardService.getRecentOrders(limit.coerceIn(1, 50))
        return ResponseEntity.ok(orders)
    }

    /**
     * Get activity feed
     * GET /admin/dashboard/activity
     */
    @GetMapping("/activity")
    @Operation(summary = "Get activity feed", description = "Returns recent system activity from audit logs")
    fun getActivityFeed(
        @RequestParam(required = false, defaultValue = "20") limit: Int
    ): ResponseEntity<List<ActivityItem>> {
        val activity = dashboardService.getActivityFeed(limit.coerceIn(1, 100))
        return ResponseEntity.ok(activity)
    }

    /**
     * Get KPIs with period selection for analytics
     * GET /admin/dashboard/kpis?period=30d
     */
    @GetMapping("/kpis")
    @Operation(summary = "Get KPIs", description = "Returns key performance indicators for the selected period")
    fun getKpis(
        @RequestParam(required = false, defaultValue = "30d") period: String
    ): ResponseEntity<KpiData> {
        val validPeriods = listOf("7d", "30d", "90d", "12m")
        val normalizedPeriod = if (period in validPeriods) period else "30d"
        val kpis = dashboardService.getKpis(normalizedPeriod)
        return ResponseEntity.ok(kpis)
    }

    /**
     * Get analytics data with charts
     * GET /admin/dashboard/analytics?period=30d
     */
    @GetMapping("/analytics")
    @Operation(summary = "Get analytics data", description = "Returns sales over time, top products, and top categories for charting")
    fun getAnalytics(
        @RequestParam(required = false, defaultValue = "30d") period: String
    ): ResponseEntity<AnalyticsData> {
        val validPeriods = listOf("7d", "30d", "90d", "12m")
        val normalizedPeriod = if (period in validPeriods) period else "30d"
        val analytics = dashboardService.getAnalyticsData(normalizedPeriod)
        return ResponseEntity.ok(analytics)
    }
}
