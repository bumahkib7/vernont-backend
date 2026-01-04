package com.vernont.repository.marketing

import com.vernont.domain.marketing.CampaignExecution
import com.vernont.domain.marketing.ExecutionStatus
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository

@Repository
interface CampaignExecutionRepository : JpaRepository<CampaignExecution, String> {

    fun findByIdAndDeletedAtIsNull(id: String): CampaignExecution?

    fun findByCampaignIdAndDeletedAtIsNull(campaignId: String): List<CampaignExecution>

    @Query("""
        SELECT ce FROM CampaignExecution ce
        WHERE ce.campaign.id = :campaignId
        AND ce.executionStatus = :status
        AND ce.deletedAt IS NULL
        ORDER BY ce.startedAt DESC
    """)
    fun findByCampaignIdAndStatus(campaignId: String, status: ExecutionStatus): List<CampaignExecution>
}
