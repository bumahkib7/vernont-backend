package com.vernont.repository.marketing

import com.vernont.domain.marketing.CampaignStatus
import com.vernont.domain.marketing.CampaignType
import com.vernont.domain.marketing.MarketingCampaign
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.EntityGraph
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository
import java.time.Instant

@Repository
interface MarketingCampaignRepository : JpaRepository<MarketingCampaign, String> {

    fun findByIdAndDeletedAtIsNull(id: String): MarketingCampaign?

    @EntityGraph(value = "MarketingCampaign.withGroup", type = EntityGraph.EntityGraphType.LOAD)
    fun findWithGroupByIdAndDeletedAtIsNull(id: String): MarketingCampaign?

    fun findByCampaignTypeAndStatusAndDeletedAtIsNull(
        type: CampaignType,
        status: CampaignStatus,
        pageable: Pageable
    ): Page<MarketingCampaign>

    @Query("""
        SELECT c FROM MarketingCampaign c
        WHERE c.status = 'SCHEDULED'
        AND c.scheduledAt <= :now
        AND c.deletedAt IS NULL
        ORDER BY c.scheduledAt ASC
    """)
    fun findScheduledCampaignsReady(now: Instant): List<MarketingCampaign>

    fun findByStatusAndDeletedAtIsNull(status: CampaignStatus, pageable: Pageable): Page<MarketingCampaign>

    fun findAllByDeletedAtIsNull(pageable: Pageable): Page<MarketingCampaign>
}
