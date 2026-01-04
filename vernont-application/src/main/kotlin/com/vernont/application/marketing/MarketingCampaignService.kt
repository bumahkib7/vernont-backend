package com.vernont.application.marketing

import com.vernont.domain.marketing.*
import com.vernont.repository.marketing.MarketingCampaignRepository
import com.vernont.repository.marketing.EmailLogRepository
import com.vernont.repository.customer.CustomerRepository
import com.vernont.repository.customer.CustomerGroupRepository
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

private val logger = KotlinLogging.logger {}

@Service
@Transactional
class MarketingCampaignService(
    private val campaignRepository: MarketingCampaignRepository,
    private val customerRepository: CustomerRepository,
    private val customerGroupRepository: CustomerGroupRepository,
    private val emailLogRepository: EmailLogRepository
) {

    fun createCampaign(request: CreateCampaignRequest): MarketingCampaign {
        logger.info { "Creating marketing campaign: ${request.name}" }

        val campaign = MarketingCampaign().apply {
            name = request.name
            description = request.description
            campaignType = request.campaignType
            emailSubject = request.emailSubject
            emailPreheader = request.emailPreheader
            emailTemplateId = request.emailTemplateId
            targetAllCustomers = request.targetAllCustomers
            config = request.config
            status = CampaignStatus.DRAFT

            if (request.targetCustomerGroupId != null) {
                targetCustomerGroup = customerGroupRepository.findById(request.targetCustomerGroupId)
                    .orElseThrow { IllegalArgumentException("Customer group not found: ${request.targetCustomerGroupId}") }
            }
        }

        val saved = campaignRepository.save(campaign)
        logger.info { "Campaign created: ${saved.id}" }
        return saved
    }

    fun updateCampaign(campaignId: String, request: UpdateCampaignRequest): MarketingCampaign {
        logger.info { "Updating campaign: $campaignId" }

        val campaign = campaignRepository.findByIdAndDeletedAtIsNull(campaignId)
            ?: throw CampaignNotFoundException("Campaign not found: $campaignId")

        // Only allow updates if campaign is in DRAFT status
        if (campaign.status != CampaignStatus.DRAFT) {
            throw IllegalStateException("Cannot update campaign in ${campaign.status} status")
        }

        request.name?.let { campaign.name = it }
        request.description?.let { campaign.description = it }
        request.emailSubject?.let { campaign.emailSubject = it }
        request.emailPreheader?.let { campaign.emailPreheader = it }
        request.emailTemplateId?.let { campaign.emailTemplateId = it }
        request.targetAllCustomers?.let { campaign.targetAllCustomers = it }
        request.config?.let { campaign.config = it }

        if (request.targetCustomerGroupId != null) {
            campaign.targetCustomerGroup = customerGroupRepository.findById(request.targetCustomerGroupId)
                .orElseThrow { IllegalArgumentException("Customer group not found: ${request.targetCustomerGroupId}") }
        }

        return campaignRepository.save(campaign)
    }

    fun scheduleCampaign(campaignId: String, scheduledAt: Instant): MarketingCampaign {
        logger.info { "Scheduling campaign: $campaignId for $scheduledAt" }

        val campaign = campaignRepository.findByIdAndDeletedAtIsNull(campaignId)
            ?: throw CampaignNotFoundException("Campaign not found: $campaignId")

        campaign.schedule(scheduledAt)
        return campaignRepository.save(campaign)
    }

    fun startCampaign(campaignId: String): MarketingCampaign {
        logger.info { "Starting campaign: $campaignId" }

        val campaign = campaignRepository.findByIdAndDeletedAtIsNull(campaignId)
            ?: throw CampaignNotFoundException("Campaign not found: $campaignId")

        campaign.start()
        return campaignRepository.save(campaign)
    }

    fun completeCampaign(campaignId: String): MarketingCampaign {
        logger.info { "Completing campaign: $campaignId" }

        val campaign = campaignRepository.findByIdAndDeletedAtIsNull(campaignId)
            ?: throw CampaignNotFoundException("Campaign not found: $campaignId")

        campaign.complete()
        return campaignRepository.save(campaign)
    }

    fun pauseCampaign(campaignId: String): MarketingCampaign {
        logger.info { "Pausing campaign: $campaignId" }

        val campaign = campaignRepository.findByIdAndDeletedAtIsNull(campaignId)
            ?: throw CampaignNotFoundException("Campaign not found: $campaignId")

        campaign.pause()
        return campaignRepository.save(campaign)
    }

    fun cancelCampaign(campaignId: String): MarketingCampaign {
        logger.info { "Cancelling campaign: $campaignId" }

        val campaign = campaignRepository.findByIdAndDeletedAtIsNull(campaignId)
            ?: throw CampaignNotFoundException("Campaign not found: $campaignId")

        campaign.cancel()
        return campaignRepository.save(campaign)
    }

    @Transactional(readOnly = true)
    fun getCampaign(campaignId: String): MarketingCampaign {
        return campaignRepository.findWithGroupByIdAndDeletedAtIsNull(campaignId)
            ?: throw CampaignNotFoundException("Campaign not found: $campaignId")
    }

    @Transactional(readOnly = true)
    fun listCampaigns(pageable: Pageable): Page<MarketingCampaign> {
        return campaignRepository.findAllByDeletedAtIsNull(pageable)
    }

    @Transactional(readOnly = true)
    fun listCampaignsByType(type: CampaignType, pageable: Pageable): Page<MarketingCampaign> {
        return campaignRepository.findByCampaignTypeAndStatusAndDeletedAtIsNull(
            type,
            CampaignStatus.DRAFT,
            pageable
        )
    }

    @Transactional(readOnly = true)
    fun listCampaignsByStatus(status: CampaignStatus, pageable: Pageable): Page<MarketingCampaign> {
        return campaignRepository.findByStatusAndDeletedAtIsNull(status, pageable)
    }

    @Transactional(readOnly = true)
    fun getScheduledCampaignsReady(): List<MarketingCampaign> {
        return campaignRepository.findScheduledCampaignsReady(Instant.now())
    }

    @Transactional(readOnly = true)
    fun getCampaignAnalytics(campaignId: String): CampaignAnalytics {
        val campaign = getCampaign(campaignId)

        val emailLogs = emailLogRepository.findByCampaignIdAndDeletedAtIsNull(campaignId)

        val opened = emailLogRepository.countOpenedByCampaignId(campaignId)
        val clicked = emailLogRepository.countClickedByCampaignId(campaignId)
        val bounced = emailLogs.count { it.status == EmailStatus.BOUNCED }.toLong()

        return CampaignAnalytics(
            campaignId = campaignId,
            campaignName = campaign.name,
            campaignType = campaign.campaignType,
            status = campaign.status,
            totalRecipients = campaign.totalRecipients,
            totalSent = campaign.totalSent,
            totalFailed = campaign.totalFailed,
            totalOpened = opened,
            totalClicked = clicked,
            totalBounced = bounced,
            openRate = if (campaign.totalSent > 0) (opened.toDouble() / campaign.totalSent) * 100 else 0.0,
            clickRate = if (campaign.totalSent > 0) (clicked.toDouble() / campaign.totalSent) * 100 else 0.0,
            clickToOpenRate = if (opened > 0) (clicked.toDouble() / opened) * 100 else 0.0,
            bounceRate = if (campaign.totalSent > 0) (bounced.toDouble() / campaign.totalSent) * 100 else 0.0,
            scheduledAt = campaign.scheduledAt,
            startedAt = campaign.startedAt,
            completedAt = campaign.completedAt
        )
    }

    fun deleteCampaign(campaignId: String) {
        logger.info { "Deleting campaign: $campaignId" }

        val campaign = campaignRepository.findByIdAndDeletedAtIsNull(campaignId)
            ?: throw CampaignNotFoundException("Campaign not found: $campaignId")

        // Only allow deletion if campaign is in DRAFT, COMPLETED, or CANCELLED status
        if (campaign.status !in listOf(CampaignStatus.DRAFT, CampaignStatus.COMPLETED, CampaignStatus.CANCELLED)) {
            throw IllegalStateException("Cannot delete campaign in ${campaign.status} status")
        }

        campaign.softDelete()
        campaignRepository.save(campaign)
    }
}

data class CreateCampaignRequest(
    val name: String,
    val description: String?,
    val campaignType: CampaignType,
    val emailSubject: String,
    val emailPreheader: String?,
    val emailTemplateId: String?,
    val targetAllCustomers: Boolean = false,
    val targetCustomerGroupId: String?,
    val config: MutableMap<String, Any?>?
)

data class UpdateCampaignRequest(
    val name: String?,
    val description: String?,
    val emailSubject: String?,
    val emailPreheader: String?,
    val emailTemplateId: String?,
    val targetAllCustomers: Boolean?,
    val targetCustomerGroupId: String?,
    val config: MutableMap<String, Any?>?
)

data class CampaignAnalytics(
    val campaignId: String,
    val campaignName: String,
    val campaignType: CampaignType,
    val status: CampaignStatus,
    val totalRecipients: Long,
    val totalSent: Long,
    val totalFailed: Long,
    val totalOpened: Long,
    val totalClicked: Long,
    val totalBounced: Long,
    val openRate: Double,
    val clickRate: Double,
    val clickToOpenRate: Double,
    val bounceRate: Double,
    val scheduledAt: Instant?,
    val startedAt: Instant?,
    val completedAt: Instant?
)

class CampaignNotFoundException(message: String) : RuntimeException(message)
