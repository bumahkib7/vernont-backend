package com.vernont.api.controller.admin

import com.vernont.application.marketing.*
import com.vernont.domain.marketing.CampaignStatus
import com.vernont.domain.marketing.CampaignType
import com.vernont.domain.marketing.MarketingCampaign
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import java.time.Instant

@RestController
@RequestMapping("/admin/marketing/campaigns")
@Tag(name = "Admin Marketing Campaigns", description = "Marketing campaign management endpoints")
class AdminMarketingCampaignController(
    private val campaignService: MarketingCampaignService
) {

    @PostMapping
    @Operation(summary = "Create a new marketing campaign")
    fun createCampaign(@RequestBody request: CreateCampaignRequest): ResponseEntity<CampaignResponse> {
        val campaign = campaignService.createCampaign(request)
        return ResponseEntity.status(HttpStatus.CREATED).body(CampaignResponse.from(campaign))
    }

    @GetMapping
    @Operation(summary = "List all campaigns")
    fun listCampaigns(pageable: Pageable): ResponseEntity<Page<CampaignResponse>> {
        val campaigns = campaignService.listCampaigns(pageable)
        return ResponseEntity.ok(campaigns.map { CampaignResponse.from(it) })
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get campaign by ID")
    fun getCampaign(@PathVariable id: String): ResponseEntity<CampaignResponse> {
        val campaign = campaignService.getCampaign(id)
        return ResponseEntity.ok(CampaignResponse.from(campaign))
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update campaign")
    fun updateCampaign(
        @PathVariable id: String,
        @RequestBody request: UpdateCampaignRequest
    ): ResponseEntity<CampaignResponse> {
        val campaign = campaignService.updateCampaign(id, request)
        return ResponseEntity.ok(CampaignResponse.from(campaign))
    }

    @PostMapping("/{id}/schedule")
    @Operation(summary = "Schedule campaign for execution")
    fun scheduleCampaign(
        @PathVariable id: String,
        @RequestBody request: ScheduleCampaignRequest
    ): ResponseEntity<CampaignResponse> {
        val campaign = campaignService.scheduleCampaign(id, request.scheduledAt)
        return ResponseEntity.ok(CampaignResponse.from(campaign))
    }

    @PostMapping("/{id}/start")
    @Operation(summary = "Manually start campaign execution")
    fun startCampaign(@PathVariable id: String): ResponseEntity<CampaignResponse> {
        val campaign = campaignService.startCampaign(id)
        return ResponseEntity.ok(CampaignResponse.from(campaign))
    }

    @PostMapping("/{id}/pause")
    @Operation(summary = "Pause running campaign")
    fun pauseCampaign(@PathVariable id: String): ResponseEntity<CampaignResponse> {
        val campaign = campaignService.pauseCampaign(id)
        return ResponseEntity.ok(CampaignResponse.from(campaign))
    }

    @PostMapping("/{id}/cancel")
    @Operation(summary = "Cancel scheduled or running campaign")
    fun cancelCampaign(@PathVariable id: String): ResponseEntity<CampaignResponse> {
        val campaign = campaignService.cancelCampaign(id)
        return ResponseEntity.ok(CampaignResponse.from(campaign))
    }

    @GetMapping("/{id}/analytics")
    @Operation(summary = "Get campaign analytics and performance metrics")
    fun getCampaignAnalytics(@PathVariable id: String): ResponseEntity<CampaignAnalytics> {
        val analytics = campaignService.getCampaignAnalytics(id)
        return ResponseEntity.ok(analytics)
    }

    @GetMapping("/by-type/{type}")
    @Operation(summary = "List campaigns by type")
    fun listCampaignsByType(
        @PathVariable type: CampaignType,
        pageable: Pageable
    ): ResponseEntity<Page<CampaignResponse>> {
        val campaigns = campaignService.listCampaignsByType(type, pageable)
        return ResponseEntity.ok(campaigns.map { CampaignResponse.from(it) })
    }

    @GetMapping("/by-status/{status}")
    @Operation(summary = "List campaigns by status")
    fun listCampaignsByStatus(
        @PathVariable status: CampaignStatus,
        pageable: Pageable
    ): ResponseEntity<Page<CampaignResponse>> {
        val campaigns = campaignService.listCampaignsByStatus(status, pageable)
        return ResponseEntity.ok(campaigns.map { CampaignResponse.from(it) })
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete campaign (soft delete)")
    fun deleteCampaign(@PathVariable id: String): ResponseEntity<Unit> {
        campaignService.deleteCampaign(id)
        return ResponseEntity.noContent().build()
    }
}

data class CampaignResponse(
    val id: String,
    val name: String,
    val description: String?,
    val campaignType: CampaignType,
    val status: CampaignStatus,
    val emailSubject: String,
    val emailPreheader: String?,
    val emailTemplateId: String?,
    val targetAllCustomers: Boolean,
    val targetCustomerGroupId: String?,
    val scheduledAt: Instant?,
    val startedAt: Instant?,
    val completedAt: Instant?,
    val totalRecipients: Long,
    val totalSent: Long,
    val totalFailed: Long,
    val createdAt: Instant,
    val updatedAt: Instant
) {
    companion object {
        fun from(campaign: MarketingCampaign) = CampaignResponse(
            id = campaign.id,
            name = campaign.name,
            description = campaign.description,
            campaignType = campaign.campaignType,
            status = campaign.status,
            emailSubject = campaign.emailSubject,
            emailPreheader = campaign.emailPreheader,
            emailTemplateId = campaign.emailTemplateId,
            targetAllCustomers = campaign.targetAllCustomers,
            targetCustomerGroupId = campaign.targetCustomerGroup?.id,
            scheduledAt = campaign.scheduledAt,
            startedAt = campaign.startedAt,
            completedAt = campaign.completedAt,
            totalRecipients = campaign.totalRecipients,
            totalSent = campaign.totalSent,
            totalFailed = campaign.totalFailed,
            createdAt = campaign.createdAt,
            updatedAt = campaign.updatedAt
        )
    }
}

data class ScheduleCampaignRequest(
    val scheduledAt: Instant
)
