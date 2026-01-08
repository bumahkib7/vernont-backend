package com.vernont.workflow.flows.customer

import com.vernont.domain.customer.CustomerActivityLog
import com.vernont.domain.customer.CustomerStatus
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
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val logger = KotlinLogging.logger {}

/**
 * Workflow for reactivating a suspended customer account.
 * Updates customer status, logs the activity, and sends notification.
 */
@Component
@WorkflowTypes(input = ActivateCustomerInput::class, output = ActivateCustomerOutput::class)
class ActivateCustomerWorkflow(
    private val customerRepository: CustomerRepository,
    private val emailService: EmailService,
    private val activityLogRepository: CustomerActivityLogRepository
) : Workflow<ActivateCustomerInput, ActivateCustomerOutput> {

    override val name = WorkflowConstants.Customer.ACTIVATE_CUSTOMER

    override suspend fun execute(
        input: ActivateCustomerInput,
        context: WorkflowContext
    ): WorkflowResult<ActivateCustomerOutput> {
        return try {
            logger.info { "Activating customer ${input.customerId}" }

            // Find customer
            val customer = customerRepository.findByIdAndDeletedAtIsNull(input.customerId)
                ?: return WorkflowResult.failure(IllegalArgumentException("Customer not found: ${input.customerId}"))

            // Check if already active
            if (customer.status == CustomerStatus.ACTIVE) {
                return WorkflowResult.failure(IllegalStateException("Customer is already active"))
            }

            // Activate the customer
            customer.activate()
            val savedCustomer = customerRepository.save(customer)

            // Log the activity
            val activityLog = CustomerActivityLog.accountActivated(
                customerId = input.customerId,
                performedBy = input.performedBy
            )
            activityLogRepository.save(activityLog)

            // Send notification email to customer (optional - best effort)
            try {
                if (customer.email != null) {
                    emailService.sendTransactionalEmail(
                        to = customer.email!!,
                        subject = "Your Vernont account has been reactivated",
                        htmlContent = buildActivationEmail(customer.firstName),
                        plainTextContent = buildActivationEmailPlainText(customer.firstName)
                    )
                }
            } catch (e: Exception) {
                logger.warn(e) { "Failed to send activation notification email to customer ${input.customerId}" }
            }

            val activatedAt = Instant.now()
            val formatter = DateTimeFormatter.ISO_INSTANT.withZone(ZoneId.of("UTC"))

            logger.info { "Customer ${input.customerId} activated successfully" }

            WorkflowResult.success(
                ActivateCustomerOutput(
                    success = true,
                    activatedAt = formatter.format(activatedAt)
                )
            )
        } catch (e: Exception) {
            logger.error(e) { "Failed to activate customer ${input.customerId}: ${e.message}" }
            WorkflowResult.failure(e)
        }
    }

    private fun buildActivationEmail(firstName: String?): String {
        val greeting = firstName?.let { "Hi $it," } ?: "Hi,"

        return """
            <!DOCTYPE html>
            <html>
            <head>
                <meta charset="utf-8">
                <meta name="viewport" content="width=device-width, initial-scale=1.0">
            </head>
            <body style="font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, 'Helvetica Neue', Arial, sans-serif; line-height: 1.6; color: #333; max-width: 600px; margin: 0 auto; padding: 20px;">
                <div style="text-align: center; padding: 20px 0; border-bottom: 1px solid #eee; margin-bottom: 30px;">
                    <h1 style="color: #000; margin: 0;">Vernont</h1>
                </div>

                <div>
                    <h2 style="color: #16a34a;">Account Reactivated</h2>
                    <p>$greeting</p>
                    <p>Great news! Your Vernont account has been reactivated.</p>

                    <div style="background-color: #f0fdf4; border-left: 4px solid #16a34a; padding: 16px; margin: 20px 0;">
                        <p style="margin: 0; color: #166534;">Your account is now fully active and you can resume shopping with us.</p>
                    </div>

                    <p>You can now:</p>
                    <ul>
                        <li>Place new orders</li>
                        <li>Access all account features</li>
                        <li>Continue earning rewards</li>
                    </ul>

                    <p>Thank you for being a valued customer. If you have any questions, please don't hesitate to contact our support team.</p>
                </div>

                <div style="border-top: 1px solid #eee; margin-top: 30px; padding-top: 20px; text-align: center; font-size: 12px; color: #666;">
                    <p>&copy; Vernont. All rights reserved.</p>
                </div>
            </body>
            </html>
        """.trimIndent()
    }

    private fun buildActivationEmailPlainText(firstName: String?): String {
        val greeting = firstName?.let { "Hi $it," } ?: "Hi,"

        return """
            Vernont - Account Reactivated

            $greeting

            Great news! Your Vernont account has been reactivated.

            Your account is now fully active and you can resume shopping with us.

            You can now:
            - Place new orders
            - Access all account features
            - Continue earning rewards

            Thank you for being a valued customer. If you have any questions, please don't hesitate to contact our support team.

            ---
            (c) Vernont. All rights reserved.
        """.trimIndent()
    }
}
