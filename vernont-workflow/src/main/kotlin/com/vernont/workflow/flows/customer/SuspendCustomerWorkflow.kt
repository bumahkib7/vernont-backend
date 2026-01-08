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
 * Workflow for suspending a customer account.
 * Updates customer status, logs the activity, and optionally sends notification.
 */
@Component
@WorkflowTypes(input = SuspendCustomerInput::class, output = SuspendCustomerOutput::class)
class SuspendCustomerWorkflow(
    private val customerRepository: CustomerRepository,
    private val emailService: EmailService,
    private val activityLogRepository: CustomerActivityLogRepository
) : Workflow<SuspendCustomerInput, SuspendCustomerOutput> {

    override val name = WorkflowConstants.Customer.SUSPEND_CUSTOMER

    override suspend fun execute(
        input: SuspendCustomerInput,
        context: WorkflowContext
    ): WorkflowResult<SuspendCustomerOutput> {
        return try {
            logger.info { "Suspending customer ${input.customerId}, reason: ${input.reason}" }

            // Find customer
            val customer = customerRepository.findByIdAndDeletedAtIsNull(input.customerId)
                ?: return WorkflowResult.failure(IllegalArgumentException("Customer not found: ${input.customerId}"))

            // Check if already suspended
            if (customer.status == CustomerStatus.SUSPENDED) {
                return WorkflowResult.failure(IllegalStateException("Customer is already suspended"))
            }

            // Suspend the customer
            customer.suspend(input.reason)
            val savedCustomer = customerRepository.save(customer)

            // Log the activity
            val activityLog = CustomerActivityLog.accountSuspended(
                customerId = input.customerId,
                reason = input.reason,
                performedBy = input.performedBy
            )
            activityLogRepository.save(activityLog)

            // Send notification email to customer (optional - best effort)
            try {
                if (customer.email != null) {
                    emailService.sendTransactionalEmail(
                        to = customer.email!!,
                        subject = "Your Vernont account has been suspended",
                        htmlContent = buildSuspensionEmail(customer.firstName, input.reason),
                        plainTextContent = buildSuspensionEmailPlainText(customer.firstName, input.reason)
                    )
                }
            } catch (e: Exception) {
                logger.warn(e) { "Failed to send suspension notification email to customer ${input.customerId}" }
            }

            val suspendedAt = savedCustomer.suspendedAt ?: Instant.now()
            val formatter = DateTimeFormatter.ISO_INSTANT.withZone(ZoneId.of("UTC"))

            logger.info { "Customer ${input.customerId} suspended successfully" }

            WorkflowResult.success(
                SuspendCustomerOutput(
                    success = true,
                    suspendedAt = formatter.format(suspendedAt)
                )
            )
        } catch (e: Exception) {
            logger.error(e) { "Failed to suspend customer ${input.customerId}: ${e.message}" }
            WorkflowResult.failure(e)
        }
    }

    private fun buildSuspensionEmail(firstName: String?, reason: String): String {
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
                    <h2 style="color: #dc2626;">Account Suspended</h2>
                    <p>$greeting</p>
                    <p>Your Vernont account has been suspended.</p>

                    <div style="background-color: #fef2f2; border-left: 4px solid #dc2626; padding: 16px; margin: 20px 0;">
                        <p style="margin: 0; color: #991b1b;"><strong>Reason:</strong> $reason</p>
                    </div>

                    <p>While your account is suspended, you will not be able to:</p>
                    <ul>
                        <li>Place new orders</li>
                        <li>Access certain account features</li>
                    </ul>

                    <p>If you believe this was done in error or have any questions, please contact our support team.</p>
                </div>

                <div style="border-top: 1px solid #eee; margin-top: 30px; padding-top: 20px; text-align: center; font-size: 12px; color: #666;">
                    <p>&copy; Vernont. All rights reserved.</p>
                </div>
            </body>
            </html>
        """.trimIndent()
    }

    private fun buildSuspensionEmailPlainText(firstName: String?, reason: String): String {
        val greeting = firstName?.let { "Hi $it," } ?: "Hi,"

        return """
            Vernont - Account Suspended

            $greeting

            Your Vernont account has been suspended.

            Reason: $reason

            While your account is suspended, you will not be able to:
            - Place new orders
            - Access certain account features

            If you believe this was done in error or have any questions, please contact our support team.

            ---
            (c) Vernont. All rights reserved.
        """.trimIndent()
    }
}
