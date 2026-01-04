package com.vernont.workflow.flows.marketing

import com.vernont.domain.customer.UserFavorite
import com.vernont.domain.marketing.CampaignType
import com.vernont.domain.marketing.EmailLog
import com.vernont.infrastructure.email.EmailService
import com.vernont.repository.customer.UserFavoriteRepository
import com.vernont.repository.marketing.EmailLogRepository
import com.vernont.repository.marketing.MarketingPreferenceRepository
import com.vernont.workflow.common.WorkflowConstants
import com.vernont.workflow.engine.Workflow
import com.vernont.workflow.engine.WorkflowContext
import com.vernont.workflow.engine.WorkflowResult
import com.vernont.workflow.engine.WorkflowTypes
import com.vernont.workflow.steps.StepResponse
import com.vernont.workflow.steps.createStep
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Component
import java.math.BigDecimal

private val logger = KotlinLogging.logger {}

data class PriceDropAlertInput(
    val checkIntervalHours: Int = 24
)

data class PriceDropAlertOutput(
    val totalChecked: Int,
    val totalAlertsSent: Int,
    val totalFailed: Int
)

/**
 * Price Drop Alert Workflow
 *
 * Steps:
 * 1. Load favorites with alerts enabled
 * 2. Filter by marketing preferences
 * 3. Check current prices
 * 4. Send price drop emails
 * 5. Log email sends
 */
@Component
@WorkflowTypes(input = PriceDropAlertInput::class, output = PriceDropAlertOutput::class)
class PriceDropAlertWorkflow(
    private val userFavoriteRepository: UserFavoriteRepository,
    private val preferenceRepository: MarketingPreferenceRepository,
    private val emailService: EmailService,
    private val emailLogRepository: EmailLogRepository
) : Workflow<PriceDropAlertInput, PriceDropAlertOutput> {

    override val name = WorkflowConstants.Marketing.PRICE_DROP_ALERT

    override suspend fun execute(
        input: PriceDropAlertInput,
        context: WorkflowContext
    ): WorkflowResult<PriceDropAlertOutput> {
        logger.info { "Executing price drop alert workflow (interval: ${input.checkIntervalHours}h)" }

        try {
            // Step 1: Load favorites with alerts enabled
            val loadFavoritesStep = createStep<Unit, List<UserFavorite>>(
                name = "load-favorites-with-alerts",
                execute = { _, ctx ->
                    val favorites = userFavoriteRepository.findByAlertEnabledTrueAndDeletedAtIsNull()
                    logger.info { "Found ${favorites.size} favorites with alerts enabled" }
                    ctx.addMetadata("totalFavorites", favorites.size)
                    StepResponse.of(favorites)
                }
            )

            val favorites = loadFavoritesStep.invoke(Unit, context).data

            var totalChecked = 0
            var totalSent = 0
            var totalFailed = 0

            // Step 2-5: Process each favorite
            for (favorite in favorites) {
                totalChecked++

                try {
                    // Step 2: Check marketing preferences
                    val checkPreferencesStep = createStep<String, Boolean>(
                        name = "check-marketing-preferences",
                        execute = { userId, ctx ->
                            val preference = preferenceRepository.findByCustomerIdAndDeletedAtIsNull(userId)
                            val canReceive = preference?.canReceiveEmail(CampaignType.PRICE_DROP) == true
                            StepResponse.of(canReceive)
                        }
                    )

                    val canReceive = checkPreferencesStep.invoke(favorite.user.id, context).data
                    if (!canReceive) {
                        logger.debug { "User ${favorite.user.id} has price drop alerts disabled" }
                        continue
                    }

                    // Step 3: Check current price (from product variants)
                    val checkPriceStep = createStep<UserFavorite, BigDecimal?>(
                        name = "check-current-price",
                        execute = { fav, ctx ->
                            val currentPrice = fav.product.variants
                                .filter { it.deletedAt == null }
                                .flatMap { it.prices }
                                .filter { it.deletedAt == null }
                                .mapNotNull { it.amount }
                                .minOrNull()
                            StepResponse.of(currentPrice)
                        }
                    )

                    val currentPrice = checkPriceStep.invoke(favorite, context).data

                    if (currentPrice == null) {
                        logger.debug { "No pricing found for product ${favorite.product.id}" }
                        continue
                    }

                    // Check if price dropped below threshold
                    val threshold = favorite.priceThreshold
                    if (threshold != null && currentPrice <= threshold) {
                        logger.info { "Price drop: user=${favorite.user.id}, product=${favorite.product.id}, price=$currentPrice <= threshold=$threshold" }

                        // Step 4: Send email
                        val sendEmailStep = createStep<Pair<UserFavorite, BigDecimal>, String>(
                            name = "send-price-drop-email",
                            execute = { (fav, price), ctx ->
                                val messageId = sendPriceDropEmail(fav, price)
                                StepResponse.of(messageId)
                            }
                        )

                        // Step 5: Log email
                        val logEmailStep = createStep<Pair<UserFavorite, String>, Unit>(
                            name = "log-email-send",
                            execute = { (fav, messageId), ctx ->
                                logEmail(fav, messageId)
                                StepResponse.of(Unit)
                            }
                        )

                        val messageId = sendEmailStep.invoke(Pair(favorite, currentPrice), context).data
                        logEmailStep.invoke(Pair(favorite, messageId), context)

                        totalSent++
                    }
                } catch (e: Exception) {
                    logger.error(e) { "Failed to process price alert for favorite ${favorite.id}" }
                    totalFailed++
                }
            }

            logger.info { "Price drop workflow completed: checked=$totalChecked, sent=$totalSent, failed=$totalFailed" }

            return WorkflowResult.success(
                PriceDropAlertOutput(
                    totalChecked = totalChecked,
                    totalAlertsSent = totalSent,
                    totalFailed = totalFailed
                )
            )
        } catch (e: Exception) {
            logger.error(e) { "Price drop alert workflow failed" }
            return WorkflowResult.failure(e)
        }
    }

    private suspend fun sendPriceDropEmail(favorite: UserFavorite, currentPrice: BigDecimal): String {
        val user = favorite.user
        val product = favorite.product

        val subject = "Price Drop Alert: ${product.title}"
        val frontendUrl = System.getenv("FRONTEND_URL") ?: "http://localhost:3000"
        val productUrl = "$frontendUrl/products/${product.handle}"

        val templateData = mapOf(
            "brand" to "Vernont",
            "preheader" to "Your favorited item just dropped in price!",
            "heading" to "Price Drop Alert!",
            "body" to """
                <p>Good news! <strong>${escapeHtml(product.title)}</strong> just dropped in price.</p>
                <p style="font-size: 24px; font-weight: bold; color: #e53e3e;">Now £${currentPrice}</p>
                <p>Click below to see this deal before it's gone!</p>
            """.trimIndent(),
            "buttonText" to "View Product",
            "buttonUrl" to productUrl,
            "footer" to "© Vernont · <a href=\"$frontendUrl/account/preferences\">Manage Preferences</a>",
            "logoUrl" to ""
        )

        emailService.sendTemplateEmail(
            to = user.email,
            templateId = "price-drop-alert",
            templateData = templateData,
            subject = subject
        )

        logger.info { "Sent price drop alert to ${user.email} for product ${product.id}" }
        return "price-drop-${favorite.id}"
    }

    @Suppress("CAST_NEVER_SUCCEEDS")
    private fun logEmail(favorite: UserFavorite, messageId: String) {
        val emailLog = EmailLog().apply {
            // Note: favorite.user is User but EmailLog expects Customer
            customer = favorite.user as com.vernont.domain.customer.Customer
            recipientEmail = favorite.user.email
            emailType = CampaignType.PRICE_DROP
            subject = "Price Drop Alert: ${favorite.product.title}"
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
