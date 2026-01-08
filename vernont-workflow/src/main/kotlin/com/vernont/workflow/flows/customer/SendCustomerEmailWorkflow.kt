package com.vernont.workflow.flows.customer

import com.vernont.domain.customer.CustomerActivityLog
import com.vernont.infrastructure.email.EmailService
import com.vernont.repository.customer.CustomerActivityLogRepository
import com.vernont.repository.customer.CustomerRepository
import com.vernont.workflow.common.WorkflowConstants
import com.vernont.workflow.engine.Workflow
import com.vernont.workflow.engine.WorkflowContext
import com.vernont.workflow.engine.WorkflowResult
import com.vernont.workflow.engine.WorkflowTypes
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Component
import java.util.UUID

private val logger = KotlinLogging.logger {}

/**
 * Workflow for sending email to a customer from the admin dashboard.
 * Creates an activity log entry for audit purposes.
 */
@Component
@WorkflowTypes(input = SendCustomerEmailInput::class, output = SendCustomerEmailOutput::class)
class SendCustomerEmailWorkflow(
    private val customerRepository: CustomerRepository,
    private val emailService: EmailService,
    private val activityLogRepository: CustomerActivityLogRepository
) : Workflow<SendCustomerEmailInput, SendCustomerEmailOutput> {

    override val name = WorkflowConstants.SendCustomerEmail.NAME

    override suspend fun execute(
        input: SendCustomerEmailInput,
        context: WorkflowContext
    ): WorkflowResult<SendCustomerEmailOutput> {
        return try {
            logger.info { "Sending email to customer ${input.customerId}: ${input.subject}" }

            // Validate customer exists
            val customer = customerRepository.findByIdAndDeletedAtIsNull(input.customerId)
                ?: return WorkflowResult.failure(IllegalArgumentException("Customer not found: ${input.customerId}"))

            // Build HTML content with basic styling
            val htmlContent = buildHtmlEmail(input.subject, input.body)

            // Send the email
            emailService.sendTransactionalEmail(
                to = input.email,
                subject = input.subject,
                htmlContent = htmlContent,
                plainTextContent = input.body
            )

            // Generate a message ID for tracking
            val messageId = "msg_${UUID.randomUUID().toString().replace("-", "").take(16)}"

            // Log the activity
            val performedBy = context.getMetadata("performedBy") as? String ?: "system"
            val activityLog = CustomerActivityLog.emailSent(
                customerId = input.customerId,
                subject = input.subject,
                performedBy = performedBy
            )
            activityLogRepository.save(activityLog)

            logger.info { "Successfully sent email to customer ${input.customerId}, messageId: $messageId" }

            WorkflowResult.success(
                SendCustomerEmailOutput(
                    success = true,
                    messageId = messageId
                )
            )
        } catch (e: Exception) {
            logger.error(e) { "Failed to send email to customer ${input.customerId}: ${e.message}" }
            WorkflowResult.failure(e)
        }
    }

    private fun buildHtmlEmail(subject: String, body: String): String {
        // Convert newlines to HTML breaks for plain text body
        val htmlBody = body.replace("\n", "<br>")

        return """
            <!DOCTYPE html>
            <html>
            <head>
                <meta charset="utf-8">
                <meta name="viewport" content="width=device-width, initial-scale=1.0">
                <title>$subject</title>
                <style>
                    body {
                        font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, 'Helvetica Neue', Arial, sans-serif;
                        line-height: 1.6;
                        color: #333;
                        max-width: 600px;
                        margin: 0 auto;
                        padding: 20px;
                    }
                    .header {
                        padding: 20px 0;
                        border-bottom: 1px solid #eee;
                        margin-bottom: 20px;
                    }
                    .content {
                        padding: 20px 0;
                    }
                    .footer {
                        padding: 20px 0;
                        border-top: 1px solid #eee;
                        margin-top: 20px;
                        font-size: 12px;
                        color: #666;
                    }
                </style>
            </head>
            <body>
                <div class="header">
                    <strong>Vernont</strong>
                </div>
                <div class="content">
                    $htmlBody
                </div>
                <div class="footer">
                    <p>&copy; Vernont. All rights reserved.</p>
                </div>
            </body>
            </html>
        """.trimIndent()
    }
}
