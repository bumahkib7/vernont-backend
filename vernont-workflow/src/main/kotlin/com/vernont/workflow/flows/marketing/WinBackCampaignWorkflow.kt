package com.vernont.workflow.flows.marketing

import com.vernont.domain.customer.Customer
import com.vernont.domain.marketing.CampaignType
import com.vernont.domain.marketing.EmailLog
import com.vernont.infrastructure.email.EmailService
import com.vernont.repository.marketing.EmailLogRepository
import com.vernont.repository.marketing.MarketingPreferenceRepository
import com.vernont.repository.marketing.UserActivityLogRepository
import com.vernont.workflow.common.WorkflowConstants
import com.vernont.workflow.engine.Workflow
import com.vernont.workflow.engine.WorkflowContext
import com.vernont.workflow.engine.WorkflowResult
import com.vernont.workflow.engine.WorkflowTypes
import com.vernont.workflow.steps.StepResponse
import com.vernont.workflow.steps.createStep
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Component
import java.time.Instant
import java.time.temporal.ChronoUnit

private val logger = KotlinLogging.logger {}

data class WinBackCampaignInput(
    val inactiveDays: List<Int> = listOf(30, 60, 90)
)

data class WinBackCampaignOutput(
    val totalProcessed: Int,
    val totalSent: Int,
    val totalFailed: Int
)

/**
 * Win-back Campaign Workflow
 *
 * Steps:
 * 1. Find inactive users
 * 2. Check marketing preferences
 * 3. Send win-back emails
 * 4. Log email sends
 */
@Component
@WorkflowTypes(input = WinBackCampaignInput::class, output = WinBackCampaignOutput::class)
class WinBackCampaignWorkflow(
    private val activityLogRepository: UserActivityLogRepository,
    private val preferenceRepository: MarketingPreferenceRepository,
    private val emailService: EmailService,
    private val emailLogRepository: EmailLogRepository
) : Workflow<WinBackCampaignInput, WinBackCampaignOutput> {

    override val name = WorkflowConstants.Marketing.WIN_BACK_CAMPAIGN

    override suspend fun execute(
        input: WinBackCampaignInput,
        context: WorkflowContext
    ): WorkflowResult<WinBackCampaignOutput> {
        logger.info { "Executing win-back campaign workflow for thresholds: ${input.inactiveDays}" }

        var totalProcessed = 0
        var totalSent = 0
        var totalFailed = 0

        try {
            // For each inactivity threshold (30, 60, 90 days)
            for (days in input.inactiveDays) {
                // Step 1: Find inactive users
                val findInactiveUsersStep = createStep<Int, List<Array<Any>>>(
                    name = "find-inactive-users",
                    execute = { inactiveDays, ctx ->
                        val threshold = Instant.now().minus(inactiveDays.toLong(), ChronoUnit.DAYS)
                        val users = activityLogRepository.findInactiveUsersSince(threshold)
                        logger.info { "Found ${users.size} users inactive for $inactiveDays days" }
                        ctx.addMetadata("inactiveDays", inactiveDays)
                        ctx.addMetadata("totalInactiveUsers", users.size)
                        StepResponse.of(users)
                    }
                )

                val inactiveUsers = findInactiveUsersStep.invoke(days, context).data

                for (userData in inactiveUsers) {
                    totalProcessed++
                    val userId = userData[0] as String

                    try {
                        // Step 2: Check marketing preferences
                        val checkPreferencesStep = createStep<String, com.vernont.domain.marketing.MarketingPreference?>(
                            name = "check-marketing-preferences",
                            execute = { uid, ctx ->
                                val preference = preferenceRepository.findByCustomerIdAndDeletedAtIsNull(uid)
                                val canReceive = preference?.canReceiveEmail(CampaignType.WIN_BACK) == true
                                StepResponse.of(if (canReceive) preference else null)
                            }
                        )

                        val preference = checkPreferencesStep.invoke(userId, context).data
                        if (preference == null) {
                            continue
                        }

                        // Step 3: Send email
                        val sendEmailStep = createStep<Triple<String, String, Int>, String>(
                            name = "send-winback-email",
                            execute = { (uid, email, inactiveDays), ctx ->
                                val messageId = sendWinBackEmail(uid, email, inactiveDays)
                                StepResponse.of(messageId)
                            }
                        )

                        // Step 4: Log email
                        val logEmailStep = createStep<Pair<Customer, String>, Unit>(
                            name = "log-email-send",
                            execute = { (customer, messageId), ctx ->
                                logEmail(customer, messageId)
                                StepResponse.of(Unit)
                            }
                        )

                        val messageId = sendEmailStep.invoke(Triple(userId, preference.customer.email, days), context).data
                        logEmailStep.invoke(Pair(preference.customer, messageId), context)

                        totalSent++
                    } catch (e: Exception) {
                        logger.error(e) { "Failed to send win-back email to user $userId" }
                        totalFailed++
                    }
                }
            }

            logger.info { "Win-back campaign completed: processed=$totalProcessed, sent=$totalSent, failed=$totalFailed" }

            return WorkflowResult.success(
                WinBackCampaignOutput(
                    totalProcessed = totalProcessed,
                    totalSent = totalSent,
                    totalFailed = totalFailed
                )
            )
        } catch (e: Exception) {
            logger.error(e) { "Win-back campaign workflow failed" }
            return WorkflowResult.failure(e)
        }
    }

    private suspend fun sendWinBackEmail(userId: String, userEmail: String, inactiveDays: Int): String {
        val subject = "We Miss You! Discover New Luxury Arrivals"

        val message = when {
            inactiveDays <= 30 -> "It's been a while since you visited Neoxus. We've added amazing new luxury items you might love!"
            inactiveDays <= 60 -> "We noticed you haven't been around lately. Come back and see what's new in luxury fashion!"
            else -> "Long time no see! We'd love to have you back. Check out our latest luxury collections!"
        }

        val frontendUrl = System.getenv("FRONTEND_URL") ?: "http://localhost:3000"
        val templateData = mapOf(
            "brand" to "Vernont",
            "preheader" to "Come back and discover new fashion",
            "heading" to "We Miss You!",
            "body" to """
                <p>$message</p>
                <p>Browse the latest products from top brands, all in one place.</p>
                <p>Find the best prices and deals on your favorite items.</p>
            """.trimIndent(),
            "buttonText" to "Explore New Arrivals",
            "buttonUrl" to frontendUrl,
            "footer" to "© Vernont · <a href=\"$frontendUrl/account/preferences\">Manage Preferences</a>",
            "logoUrl" to ""
        )

        emailService.sendTemplateEmail(
            to = userEmail,
            templateId = "win-back",
            templateData = templateData,
            subject = subject
        )

        logger.info { "Sent win-back email to $userEmail (inactive $inactiveDays days)" }
        return "win-back-$userId-${inactiveDays}d"
    }

    private fun logEmail(customer: Customer, messageId: String) {
        val emailLog = EmailLog().apply {
            this.customer = customer
            recipientEmail = customer.email
            emailType = CampaignType.WIN_BACK
            subject = "We Miss You! Discover New Luxury Arrivals"
            markSent(messageId)
        }
        emailLogRepository.save(emailLog)
    }
}
