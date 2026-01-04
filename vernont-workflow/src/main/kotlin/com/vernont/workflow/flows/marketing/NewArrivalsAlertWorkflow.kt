package com.vernont.workflow.flows.marketing

import com.vernont.domain.customer.Customer
import com.vernont.domain.marketing.CampaignType
import com.vernont.domain.marketing.EmailLog
import com.vernont.infrastructure.email.EmailService
import com.vernont.repository.marketing.EmailLogRepository
import com.vernont.repository.marketing.MarketingPreferenceRepository
import com.vernont.repository.marketing.UserBrandInterestRepository
import com.vernont.repository.product.ProductRepository
import com.vernont.workflow.common.WorkflowConstants
import com.vernont.workflow.engine.Workflow
import com.vernont.workflow.engine.WorkflowContext
import com.vernont.workflow.engine.WorkflowResult
import com.vernont.workflow.engine.WorkflowTypes
import com.vernont.workflow.steps.StepResponse
import com.vernont.workflow.steps.createStep
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Component

private val logger = KotlinLogging.logger {}

data class NewArrivalsInput(
    val productId: String
)

data class NewArrivalsOutput(
    val totalNotified: Int,
    val totalFailed: Int
)

/**
 * New Arrivals Alert Workflow
 *
 * Steps:
 * 1. Load product
 * 2. Find interested users
 * 3. Check marketing preferences
 * 4. Send new arrival emails
 * 5. Log email sends
 */
@Component
@WorkflowTypes(input = NewArrivalsInput::class, output = NewArrivalsOutput::class)
class NewArrivalsAlertWorkflow(
    private val productRepository: ProductRepository,
    private val userBrandInterestRepository: UserBrandInterestRepository,
    private val preferenceRepository: MarketingPreferenceRepository,
    private val emailService: EmailService,
    private val emailLogRepository: EmailLogRepository
) : Workflow<NewArrivalsInput, NewArrivalsOutput> {

    override val name = WorkflowConstants.Marketing.NEW_ARRIVALS_ALERT

    override suspend fun execute(
        input: NewArrivalsInput,
        context: WorkflowContext
    ): WorkflowResult<NewArrivalsOutput> {
        logger.info { "Executing new arrivals alert workflow for product: ${input.productId}" }

        try {
            // Step 1: Load product
            val loadProductStep = createStep<String, com.vernont.domain.product.Product?>(
                name = "load-product",
                execute = { productId, ctx ->
                    val product = productRepository.findById(productId).orElse(null)
                    if (product != null) {
                        ctx.addMetadata("productTitle", product.title)
                        ctx.addMetadata("brandId", product.brand?.id ?: "")
                        ctx.addMetadata("brandName", product.brand?.name ?: "")
                    }
                    StepResponse.of(product)
                }
            )

            val product = loadProductStep.invoke(input.productId, context).data
            if (product == null) {
                logger.warn { "Product not found: ${input.productId}" }
                return WorkflowResult.success(NewArrivalsOutput(0, 0))
            }

            val brandId = product.brand?.id
            if (brandId == null) {
                logger.debug { "Product ${input.productId} has no brand, skipping new arrivals alerts" }
                return WorkflowResult.success(NewArrivalsOutput(0, 0))
            }

            // Step 2: Find interested users
            val findInterestedUsersStep = createStep<String, List<com.vernont.domain.marketing.UserBrandInterest>>(
                name = "find-interested-users",
                execute = { brand, ctx ->
                    val users = userBrandInterestRepository.findTopUsersByBrandId(brand)
                    logger.info { "Found ${users.size} users interested in brand ${product.brand?.name}" }
                    ctx.addMetadata("totalInterestedUsers", users.size)
                    StepResponse.of(users)
                }
            )

            val interestedUsers = findInterestedUsersStep.invoke(brandId, context).data

            var totalNotified = 0
            var totalFailed = 0

            // Step 3-5: Process each interested user
            for (userInterest in interestedUsers) {
                try {
                    // Step 3: Check marketing preferences
                    val checkPreferencesStep = createStep<String, com.vernont.domain.marketing.MarketingPreference?>(
                        name = "check-marketing-preferences",
                        execute = { userId, ctx ->
                            val preference = preferenceRepository.findByCustomerIdAndDeletedAtIsNull(userId)
                            val canReceive = preference?.canReceiveEmail(CampaignType.NEW_ARRIVALS) == true
                            StepResponse.of(if (canReceive) preference else null)
                        }
                    )

                    val preference = checkPreferencesStep.invoke(userInterest.userId, context).data
                    if (preference == null) {
                        continue
                    }

                    // Step 4: Send email
                    val sendEmailStep = createStep<Triple<String, String, com.vernont.domain.product.Product>, String>(
                        name = "send-new-arrival-email",
                        execute = { (userId, userEmail, prod), ctx ->
                            val messageId = sendNewArrivalEmail(userId, userEmail, prod)
                            StepResponse.of(messageId)
                        }
                    )

                    // Step 5: Log email
                    val logEmailStep = createStep<Pair<Customer, String>, Unit>(
                        name = "log-email-send",
                        execute = { (customer, messageId), ctx ->
                            logEmail(customer, messageId, product.title)
                            StepResponse.of(Unit)
                        }
                    )

                    val messageId = sendEmailStep.invoke(Triple(userInterest.userId, preference.customer.email, product), context).data
                    logEmailStep.invoke(Pair(preference.customer, messageId), context)

                    totalNotified++
                } catch (e: Exception) {
                    logger.error(e) { "Failed to send new arrival alert to user ${userInterest.userId}" }
                    totalFailed++
                }
            }

            logger.info { "New arrivals alerts completed: notified=$totalNotified, failed=$totalFailed" }

            return WorkflowResult.success(
                NewArrivalsOutput(
                    totalNotified = totalNotified,
                    totalFailed = totalFailed
                )
            )
        } catch (e: Exception) {
            logger.error(e) { "New arrivals alert workflow failed" }
            return WorkflowResult.failure(e)
        }
    }

    private suspend fun sendNewArrivalEmail(
        userId: String,
        userEmail: String,
        product: com.vernont.domain.product.Product
    ): String {
        val subject = "New Arrival: ${product.brand?.name} - ${product.title}"
        val frontendUrl = System.getenv("FRONTEND_URL") ?: "http://localhost:3000"
        val productUrl = "$frontendUrl/products/${product.handle}"

        val templateData = mapOf(
            "brand" to "Vernont",
            "preheader" to "New ${product.brand?.name} just arrived!",
            "heading" to "New Arrival from ${product.brand?.name}",
            "body" to """
                <p>Great news! A new product from <strong>${escapeHtml(product.brand?.name ?: "")}</strong> just arrived at Vernont.</p>
                <h3 style="margin: 16px 0;">${escapeHtml(product.title)}</h3>
                <p>Check it out before it's gone!</p>
            """.trimIndent(),
            "buttonText" to "View Product",
            "buttonUrl" to productUrl,
            "footer" to "© Vernont · <a href=\"$frontendUrl/account/preferences\">Manage Preferences</a>",
            "logoUrl" to ""
        )

        emailService.sendTemplateEmail(
            to = userEmail,
            templateId = "new-arrivals",
            templateData = templateData,
            subject = subject
        )

        logger.info { "Sent new arrival alert to $userEmail for product ${product.id}" }
        return "new-arrival-${product.id}"
    }

    private fun logEmail(customer: Customer, messageId: String, productTitle: String) {
        val emailLog = EmailLog().apply {
            this.customer = customer
            recipientEmail = customer.email
            emailType = CampaignType.NEW_ARRIVALS
            subject = "New Arrival: ${productTitle}"
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
