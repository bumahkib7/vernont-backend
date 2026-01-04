package com.vernont.repository.marketing

import com.vernont.domain.marketing.EmailLog
import com.vernont.domain.marketing.EmailStatus
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository
import java.time.Instant

@Repository
interface EmailLogRepository : JpaRepository<EmailLog, String> {

    fun findByIdAndDeletedAtIsNull(id: String): EmailLog?

    fun findByCampaignIdAndDeletedAtIsNull(campaignId: String): List<EmailLog>

    fun findByCustomerIdAndDeletedAtIsNull(customerId: String, pageable: Pageable): Page<EmailLog>

    fun findByMailersendMessageId(messageId: String): EmailLog?

    @Query("""
        SELECT el FROM EmailLog el
        WHERE el.campaign.id = :campaignId
        AND el.status = :status
        AND el.deletedAt IS NULL
    """)
    fun findByCampaignIdAndStatus(campaignId: String, status: EmailStatus): List<EmailLog>

    @Query("""
        SELECT COUNT(el) FROM EmailLog el
        WHERE el.campaign.id = :campaignId
        AND el.openedAt IS NOT NULL
        AND el.deletedAt IS NULL
    """)
    fun countOpenedByCampaignId(campaignId: String): Long

    @Query("""
        SELECT COUNT(el) FROM EmailLog el
        WHERE el.campaign.id = :campaignId
        AND el.clickedAt IS NOT NULL
        AND el.deletedAt IS NULL
    """)
    fun countClickedByCampaignId(campaignId: String): Long
}
