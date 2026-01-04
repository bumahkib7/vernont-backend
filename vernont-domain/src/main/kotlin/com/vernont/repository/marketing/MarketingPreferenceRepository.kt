package com.vernont.repository.marketing

import com.vernont.domain.marketing.MarketingPreference
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository

@Repository
interface MarketingPreferenceRepository : JpaRepository<MarketingPreference, String> {

    fun findByCustomerIdAndDeletedAtIsNull(customerId: String): MarketingPreference?

    @Query("""
        SELECT mp FROM MarketingPreference mp
        WHERE mp.marketingEmailsEnabled = true
        AND mp.deletedAt IS NULL
    """)
    fun findAllWithMarketingEnabled(): List<MarketingPreference>

    @Query("""
        SELECT mp FROM MarketingPreference mp
        WHERE mp.priceDropAlertsEnabled = true
        AND mp.marketingEmailsEnabled = true
        AND mp.deletedAt IS NULL
    """)
    fun findAllWithPriceAlertsEnabled(): List<MarketingPreference>

    @Query("""
        SELECT mp FROM MarketingPreference mp
        WHERE mp.newArrivalsEnabled = true
        AND mp.marketingEmailsEnabled = true
        AND mp.deletedAt IS NULL
    """)
    fun findAllWithNewArrivalsEnabled(): List<MarketingPreference>

    @Query("""
        SELECT mp FROM MarketingPreference mp
        WHERE mp.weeklyDigestEnabled = true
        AND mp.marketingEmailsEnabled = true
        AND mp.deletedAt IS NULL
    """)
    fun findAllWithWeeklyDigestEnabled(): List<MarketingPreference>
}
