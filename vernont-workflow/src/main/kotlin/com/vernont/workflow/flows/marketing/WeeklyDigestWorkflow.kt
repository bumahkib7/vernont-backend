package com.vernont.workflow.flows.marketing

import com.vernont.domain.customer.Customer
import com.vernont.domain.marketing.CampaignType
import com.vernont.domain.marketing.EmailLog
import com.vernont.infrastructure.email.EmailService
import com.vernont.repository.marketing.EmailLogRepository
import com.vernont.repository.marketing.MarketingPreferenceRepository
import com.vernont.repository.product.ProductRepository
import com.vernont.workflow.common.WorkflowConstants
import com.vernont.workflow.engine.Workflow
import com.vernont.workflow.engine.WorkflowContext
import com.vernont.workflow.engine.WorkflowResult
import com.vernont.workflow.engine.WorkflowTypes
import com.vernont.workflow.steps.StepResponse
import com.vernont.workflow.steps.createStep
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Component

private val logger = KotlinLogging.logger {}

data class WeeklyDigestInput(
    val maxProducts: Int = 10
)

data class WeeklyDigestOutput(
    val totalSent: Int,
    val totalFailed: Int
)

/**
 * Weekly Digest Workflow
 *
 * Steps:
 * 1. Load top products
 * 2. Get digest subscribers
 * 3. Send digest emails
 * 4. Log email sends
 */
@Component
@WorkflowTypes(input = WeeklyDigestInput::class, output = WeeklyDigestOutput::class)
class WeeklyDigestWorkflow(
    private val productRepository: ProductRepository,
    private val preferenceRepository: MarketingPreferenceRepository,
    private val emailService: EmailService,
    private val emailLogRepository: EmailLogRepository
) : Workflow<WeeklyDigestInput, WeeklyDigestOutput> {

    override val name = WorkflowConstants.Marketing.WEEKLY_DIGEST

    override suspend fun execute(
        input: WeeklyDigestInput,
        context: WorkflowContext
    ): WorkflowResult<WeeklyDigestOutput> {
        logger.info { "Executing weekly digest workflow" }

        try {
            // Step 1: Load top products
            val loadTopProductsStep = createStep<Int, List<com.vernont.domain.product.Product>>(
                name = "load-top-products",
                execute = { maxProducts, ctx ->
                    val products = productRepository.findAll(PageRequest.of(0, maxProducts)).content
                    logger.info { "Loaded ${products.size} top products for digest" }
                    ctx.addMetadata("totalProducts", products.size)
                    StepResponse.of(products)
                }
            )

            val topProducts = loadTopProductsStep.invoke(input.maxProducts, context).data

            if (topProducts.isEmpty()) {
                logger.warn { "No products found for weekly digest" }
                return WorkflowResult.success(WeeklyDigestOutput(0, 0))
            }

            // Step 2: Get digest subscribers
            val getSubscribersStep = createStep<Unit, List<com.vernont.domain.marketing.MarketingPreference>>(
                name = "get-digest-subscribers",
                execute = { _, ctx ->
                    val prefs = preferenceRepository.findAllWithWeeklyDigestEnabled()
                    logger.info { "Sending weekly digest to ${prefs.size} subscribers" }
                    ctx.addMetadata("totalSubscribers", prefs.size)
                    StepResponse.of(prefs)
                }
            )

            val preferences = getSubscribersStep.invoke(Unit, context).data

            var totalSent = 0
            var totalFailed = 0

            // Step 3-4: Process each subscriber
            for (preference in preferences) {
                try {
                    // Step 3: Send email
                    val sendEmailStep = createStep<Pair<String, List<com.vernont.domain.product.Product>>, String>(
                        name = "send-digest-email",
                        execute = { (email, products), ctx ->
                            val messageId = sendWeeklyDigest(email, products)
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

                    val messageId = sendEmailStep.invoke(Pair(preference.customer.email, topProducts), context).data
                    logEmailStep.invoke(Pair(preference.customer, messageId), context)

                    totalSent++
                } catch (e: Exception) {
                    logger.error(e) { "Failed to send weekly digest to ${preference.customer.email}" }
                    totalFailed++
                }
            }

            logger.info { "Weekly digest completed: sent=$totalSent, failed=$totalFailed" }

            return WorkflowResult.success(
                WeeklyDigestOutput(
                    totalSent = totalSent,
                    totalFailed = totalFailed
                )
            )
        } catch (e: Exception) {
            logger.error(e) { "Weekly digest workflow failed" }
            return WorkflowResult.failure(e)
        }
    }

    private suspend fun sendWeeklyDigest(
        userEmail: String,
        products: List<com.vernont.domain.product.Product>
    ): String {
        val subject = "Your Weekly Luxury Deals Digest"

        // Build product list HTML
        val productListHtml = products.take(10).joinToString("\n") { product ->
            val minPrice = product.variants
                .filter { it.deletedAt == null }
                .flatMap { it.prices }
                .filter { it.deletedAt == null }
                .mapNotNull { it.amount }
                .minOrNull()

            val priceHtml = if (minPrice != null) {
                "<p style=\"font-weight: bold; color: #e53e3e; margin: 4px 0;\">From £$minPrice</p>"
            } else {
                ""
            }

            val frontendUrl = System.getenv("FRONTEND_URL") ?: "http://localhost:3000"
            """
            <div style="margin-bottom: 24px; padding-bottom: 24px; border-bottom: 1px solid #e5e7eb;">
                <h4 style="margin: 0 0 8px 0;">${escapeHtml(product.title)}</h4>
                <p style="margin: 0 0 8px 0; color: #6b7280;">${escapeHtml(product.brand?.name ?: "")}</p>
                $priceHtml
                <a href="$frontendUrl/products/${product.handle}" style="color: #111111; text-decoration: underline;">View Product →</a>
            </div>
            """.trimIndent()
        }

        val frontendUrl = System.getenv("FRONTEND_URL") ?: "http://localhost:3000"
        val templateData = mapOf(
            "brand" to "Vernont",
            "preheader" to "This week's best fashion deals",
            "heading" to "This Week's Best Deals",
            "body" to """
                <p>Here are the top fashion deals curated just for you this week:</p>
                <div style="margin-top: 24px;">
                    $productListHtml
                </div>
                <p style="margin-top: 24px;">Browse more at <a href="$frontendUrl" style="color: #111111;">Vernont</a></p>
            """.trimIndent(),
            "buttonText" to "Shop All Deals",
            "buttonUrl" to "$frontendUrl/sale",
            "footer" to "© Vernont · <a href=\"$frontendUrl/account/preferences\">Manage Preferences</a>",
            "logoUrl" to ""
        )

        emailService.sendTemplateEmail(
            to = userEmail,
            templateId = "weekly-digest",
            templateData = templateData,
            subject = subject
        )

        logger.info { "Sent weekly digest to $userEmail" }
        return "weekly-digest-${System.currentTimeMillis()}"
    }

    private fun logEmail(customer: Customer, messageId: String) {
        val emailLog = EmailLog().apply {
            this.customer = customer
            recipientEmail = customer.email
            emailType = CampaignType.WEEKLY_DIGEST
            subject = "Your Weekly Luxury Deals Digest"
            markSent(messageId)
        }
        emailLogRepository.save(emailLog)
    }

    private fun escapeHtml(text: String): String {
        return text
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&#39;")
    }
}
