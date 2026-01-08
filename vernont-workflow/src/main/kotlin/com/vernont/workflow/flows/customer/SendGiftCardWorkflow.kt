package com.vernont.workflow.flows.customer

import com.vernont.domain.customer.CustomerActivityLog
import com.vernont.domain.giftcard.GiftCard
import com.vernont.infrastructure.email.EmailService
import com.vernont.repository.customer.CustomerActivityLogRepository
import com.vernont.repository.customer.CustomerRepository
import com.vernont.repository.giftcard.GiftCardRepository
import com.vernont.workflow.common.WorkflowConstants
import com.vernont.workflow.engine.Workflow
import com.vernont.workflow.engine.WorkflowContext
import com.vernont.workflow.engine.WorkflowResult
import com.vernont.workflow.engine.WorkflowTypes
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Component
import java.math.BigDecimal
import java.time.Instant
import java.time.temporal.ChronoUnit

private val logger = KotlinLogging.logger {}

/**
 * Workflow for sending a gift card to a customer.
 * Creates a new gift card, sends email notification, and logs the activity.
 */
@Component
@WorkflowTypes(input = SendGiftCardInput::class, output = SendGiftCardOutput::class)
class SendGiftCardWorkflow(
    private val customerRepository: CustomerRepository,
    private val giftCardRepository: GiftCardRepository,
    private val emailService: EmailService,
    private val activityLogRepository: CustomerActivityLogRepository
) : Workflow<SendGiftCardInput, SendGiftCardOutput> {

    override val name = WorkflowConstants.SendGiftCard.NAME

    override suspend fun execute(
        input: SendGiftCardInput,
        context: WorkflowContext
    ): WorkflowResult<SendGiftCardOutput> {
        return try {
            logger.info { "Creating gift card for customer ${input.customerId}, amount: ${input.amount} ${input.currencyCode}" }

            // Validate customer exists
            val customer = customerRepository.findByIdAndDeletedAtIsNull(input.customerId)
                ?: return WorkflowResult.failure(IllegalArgumentException("Customer not found: ${input.customerId}"))

            val performedBy = context.getMetadata("performedBy") as? String

            // Calculate expiration if specified
            val expiresAt = input.expiresInDays?.let {
                Instant.now().plus(it.toLong(), ChronoUnit.DAYS)
            }

            // Create the gift card (amount is in cents)
            val giftCard = GiftCard.create(
                amount = input.amount,
                currencyCode = input.currencyCode,
                issuedToCustomerId = input.customerId,
                issuedByUserId = performedBy ?: "system",
                message = input.message,
                recipientEmail = input.customerEmail,
                recipientName = input.customerName,
                expiresAt = expiresAt
            )
            val savedGiftCard = giftCardRepository.save(giftCard)

            // Convert to decimal for email display
            val amountDecimal = BigDecimal(input.amount).divide(BigDecimal(100))

            // Send the email
            var emailSent = false
            try {
                val htmlContent = buildGiftCardEmail(
                    customerName = input.customerName,
                    giftCardCode = savedGiftCard.code,
                    amount = amountDecimal,
                    currencyCode = input.currencyCode,
                    message = input.message,
                    expiresAt = expiresAt
                )

                emailService.sendTransactionalEmail(
                    to = input.customerEmail,
                    subject = "You've received a gift card from Vernont!",
                    htmlContent = htmlContent,
                    plainTextContent = buildPlainTextEmail(
                        customerName = input.customerName,
                        giftCardCode = savedGiftCard.code,
                        amount = amountDecimal,
                        currencyCode = input.currencyCode,
                        message = input.message,
                        expiresAt = expiresAt
                    )
                )
                emailSent = true
            } catch (e: Exception) {
                logger.error(e) { "Failed to send gift card email, but gift card was created: ${savedGiftCard.id}" }
            }

            // Log the activity
            val activityLog = CustomerActivityLog.giftCardSent(
                customerId = input.customerId,
                amount = input.amount,
                giftCardId = savedGiftCard.id,
                performedBy = performedBy ?: "system"
            )
            activityLogRepository.save(activityLog)

            logger.info { "Gift card created: ${savedGiftCard.id}, code: ${savedGiftCard.code}, emailSent: $emailSent" }

            WorkflowResult.success(
                SendGiftCardOutput(
                    giftCardId = savedGiftCard.id,
                    giftCardCode = savedGiftCard.code,
                    amount = input.amount,
                    emailSent = emailSent
                )
            )
        } catch (e: Exception) {
            logger.error(e) { "Failed to create gift card for customer ${input.customerId}: ${e.message}" }
            WorkflowResult.failure(e)
        }
    }

    private fun buildGiftCardEmail(
        customerName: String,
        giftCardCode: String,
        amount: BigDecimal,
        currencyCode: String,
        message: String?,
        expiresAt: Instant?
    ): String {
        val formattedAmount = formatCurrency(amount, currencyCode)
        val expirationText = expiresAt?.let {
            "<p style=\"color: #666; font-size: 14px;\">This gift card expires on ${formatDate(it)}.</p>"
        } ?: ""
        val personalMessage = message?.let {
            """
            <div style="background-color: #f8f9fa; border-radius: 8px; padding: 16px; margin: 20px 0;">
                <p style="font-style: italic; color: #555; margin: 0;">"$it"</p>
            </div>
            """.trimIndent()
        } ?: ""

        return """
            <!DOCTYPE html>
            <html>
            <head>
                <meta charset="utf-8">
                <meta name="viewport" content="width=device-width, initial-scale=1.0">
                <title>You've received a gift card!</title>
            </head>
            <body style="font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, 'Helvetica Neue', Arial, sans-serif; line-height: 1.6; color: #333; max-width: 600px; margin: 0 auto; padding: 20px;">
                <div style="text-align: center; padding: 20px 0; border-bottom: 1px solid #eee; margin-bottom: 30px;">
                    <h1 style="color: #000; margin: 0;">Vernont</h1>
                </div>

                <div style="text-align: center;">
                    <h2 style="color: #333; margin-bottom: 10px;">You've received a gift card!</h2>
                    <p>Hi $customerName,</p>
                    <p>Great news! You've been gifted a Vernont gift card.</p>

                    $personalMessage

                    <div style="background: linear-gradient(135deg, #667eea 0%, #764ba2 100%); border-radius: 12px; padding: 30px; margin: 30px 0; color: white;">
                        <p style="font-size: 14px; margin: 0 0 10px 0; opacity: 0.9;">Gift Card Value</p>
                        <p style="font-size: 36px; font-weight: bold; margin: 0 0 20px 0;">$formattedAmount</p>
                        <p style="font-size: 14px; margin: 0 0 5px 0; opacity: 0.9;">Your Code</p>
                        <p style="font-size: 24px; font-family: monospace; letter-spacing: 3px; margin: 0; background: rgba(255,255,255,0.2); padding: 10px 20px; border-radius: 6px;">$giftCardCode</p>
                    </div>

                    <p>Use this code at checkout to redeem your gift card.</p>
                    $expirationText
                </div>

                <div style="border-top: 1px solid #eee; margin-top: 30px; padding-top: 20px; text-align: center; font-size: 12px; color: #666;">
                    <p>&copy; Vernont. All rights reserved.</p>
                </div>
            </body>
            </html>
        """.trimIndent()
    }

    private fun buildPlainTextEmail(
        customerName: String,
        giftCardCode: String,
        amount: BigDecimal,
        currencyCode: String,
        message: String?,
        expiresAt: Instant?
    ): String {
        val formattedAmount = formatCurrency(amount, currencyCode)
        val expirationText = expiresAt?.let { "\nThis gift card expires on ${formatDate(it)}." } ?: ""
        val personalMessage = message?.let { "\n\"$it\"\n" } ?: ""

        return """
            Vernont - You've received a gift card!

            Hi $customerName,

            Great news! You've been gifted a Vernont gift card.
            $personalMessage
            Gift Card Value: $formattedAmount
            Your Code: $giftCardCode

            Use this code at checkout to redeem your gift card.$expirationText

            ---
            (c) Vernont. All rights reserved.
        """.trimIndent()
    }

    private fun formatCurrency(amount: BigDecimal, currencyCode: String): String {
        val symbol = when (currencyCode.uppercase()) {
            "GBP" -> "£"
            "USD" -> "$"
            "EUR" -> "€"
            else -> currencyCode
        }
        return "$symbol${amount.setScale(2)}"
    }

    private fun formatDate(instant: Instant): String {
        val formatter = java.time.format.DateTimeFormatter.ofPattern("MMMM d, yyyy")
            .withZone(java.time.ZoneId.of("UTC"))
        return formatter.format(instant)
    }
}
