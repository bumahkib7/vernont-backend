package com.vernont.workflow.flows.customer

import com.vernont.domain.customer.CustomerActivityLog
import com.vernont.domain.customer.CustomerTier
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

private val logger = KotlinLogging.logger {}

/**
 * Workflow for manually changing a customer's tier.
 * Updates customer tier with override flag, logs the activity, and sends notification.
 */
@Component
@WorkflowTypes(input = ChangeTierInput::class, output = ChangeTierOutput::class)
class ChangeTierWorkflow(
    private val customerRepository: CustomerRepository,
    private val emailService: EmailService,
    private val activityLogRepository: CustomerActivityLogRepository
) : Workflow<ChangeTierInput, ChangeTierOutput> {

    override val name = WorkflowConstants.Customer.CHANGE_TIER

    override suspend fun execute(
        input: ChangeTierInput,
        context: WorkflowContext
    ): WorkflowResult<ChangeTierOutput> {
        return try {
            logger.info { "Changing tier for customer ${input.customerId} to ${input.newTier}" }

            // Find customer
            val customer = customerRepository.findByIdAndDeletedAtIsNull(input.customerId)
                ?: return WorkflowResult.failure(IllegalArgumentException("Customer not found: ${input.customerId}"))

            // Parse new tier
            val newTier = try {
                CustomerTier.valueOf(input.newTier.uppercase())
            } catch (e: IllegalArgumentException) {
                return WorkflowResult.failure(IllegalArgumentException("Invalid tier: ${input.newTier}. Valid tiers: ${CustomerTier.entries.joinToString()}"))
            }

            val previousTier = customer.tier

            // Check if tier is actually changing
            if (previousTier == newTier) {
                return WorkflowResult.failure(IllegalStateException("Customer is already ${newTier.displayName}"))
            }

            // Update the tier manually (sets override flag)
            customer.setTierManually(newTier)
            val savedCustomer = customerRepository.save(customer)

            // Log the activity
            val activityLog = CustomerActivityLog.tierChanged(
                customerId = input.customerId,
                previousTier = previousTier,
                newTier = newTier,
                reason = input.reason,
                performedBy = input.performedBy
            )
            activityLogRepository.save(activityLog)

            // Send notification email (best effort)
            try {
                if (customer.email != null) {
                    val isUpgrade = newTier.ordinal > previousTier.ordinal
                    emailService.sendTransactionalEmail(
                        to = customer.email!!,
                        subject = if (isUpgrade) "Congratulations! You've been upgraded to ${newTier.displayName}!"
                                  else "Your Vernont tier has been updated",
                        htmlContent = buildTierChangeEmail(customer.firstName, previousTier, newTier, isUpgrade),
                        plainTextContent = buildTierChangeEmailPlainText(customer.firstName, previousTier, newTier, isUpgrade)
                    )
                }
            } catch (e: Exception) {
                logger.warn(e) { "Failed to send tier change notification email to customer ${input.customerId}" }
            }

            logger.info { "Customer ${input.customerId} tier changed from ${previousTier.name} to ${newTier.name}" }

            WorkflowResult.success(
                ChangeTierOutput(
                    success = true,
                    previousTier = previousTier.name,
                    newTier = newTier.name
                )
            )
        } catch (e: Exception) {
            logger.error(e) { "Failed to change tier for customer ${input.customerId}: ${e.message}" }
            WorkflowResult.failure(e)
        }
    }

    private fun buildTierChangeEmail(
        firstName: String?,
        previousTier: CustomerTier,
        newTier: CustomerTier,
        isUpgrade: Boolean
    ): String {
        val greeting = firstName?.let { "Hi $it," } ?: "Hi,"
        val title = if (isUpgrade) "You've been upgraded!" else "Your tier has been updated"
        val headerColor = if (isUpgrade) "#16a34a" else "#2563eb"

        val benefitsList = buildBenefitsList(newTier)

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

                <div style="text-align: center;">
                    <h2 style="color: $headerColor;">$title</h2>
                    <p>$greeting</p>
                    ${if (isUpgrade) "<p>Congratulations! Your Vernont membership has been upgraded.</p>"
                      else "<p>Your Vernont membership tier has been updated.</p>"}

                    <div style="background: ${getTierGradient(newTier)}; border-radius: 12px; padding: 30px; margin: 30px 0; color: white; text-align: center;">
                        <p style="font-size: 14px; margin: 0 0 5px 0; opacity: 0.9;">Your New Tier</p>
                        <p style="font-size: 32px; font-weight: bold; margin: 0;">${newTier.displayName}</p>
                        ${if (isUpgrade) """
                        <p style="font-size: 14px; margin: 10px 0 0 0; opacity: 0.7;">
                            Previously: ${previousTier.displayName}
                        </p>
                        """ else ""}
                    </div>

                    <div style="text-align: left; background-color: #f8f9fa; border-radius: 8px; padding: 20px; margin: 20px 0;">
                        <h3 style="margin-top: 0;">Your ${newTier.displayName} Benefits:</h3>
                        $benefitsList
                    </div>

                    <p>Thank you for being a valued Vernont customer!</p>
                </div>

                <div style="border-top: 1px solid #eee; margin-top: 30px; padding-top: 20px; text-align: center; font-size: 12px; color: #666;">
                    <p>&copy; Vernont. All rights reserved.</p>
                </div>
            </body>
            </html>
        """.trimIndent()
    }

    private fun buildTierChangeEmailPlainText(
        firstName: String?,
        previousTier: CustomerTier,
        newTier: CustomerTier,
        isUpgrade: Boolean
    ): String {
        val greeting = firstName?.let { "Hi $it," } ?: "Hi,"
        val title = if (isUpgrade) "You've been upgraded!" else "Your tier has been updated"

        return """
            Vernont - $title

            $greeting

            ${if (isUpgrade) "Congratulations! Your Vernont membership has been upgraded."
              else "Your Vernont membership tier has been updated."}

            Your New Tier: ${newTier.displayName}
            ${if (isUpgrade) "Previously: ${previousTier.displayName}" else ""}

            Your ${newTier.displayName} Benefits:
            ${getBenefitsPlainText(newTier)}

            Thank you for being a valued Vernont customer!

            ---
            (c) Vernont. All rights reserved.
        """.trimIndent()
    }

    private fun getTierGradient(tier: CustomerTier): String = when (tier) {
        CustomerTier.BRONZE -> "linear-gradient(135deg, #92400e 0%, #78350f 100%)"
        CustomerTier.SILVER -> "linear-gradient(135deg, #6b7280 0%, #4b5563 100%)"
        CustomerTier.GOLD -> "linear-gradient(135deg, #ca8a04 0%, #a16207 100%)"
        CustomerTier.PLATINUM -> "linear-gradient(135deg, #667eea 0%, #764ba2 100%)"
    }

    private fun buildBenefitsList(tier: CustomerTier): String {
        val benefits = mutableListOf<String>()

        if (tier.discountPercent > 0) {
            benefits.add("${tier.discountPercent}% discount on all orders")
        }
        if (tier.freeShipping) {
            benefits.add("Free shipping on all orders")
        }
        benefits.add("Priority customer support")
        if (tier == CustomerTier.GOLD || tier == CustomerTier.PLATINUM) {
            benefits.add("Early access to new products")
        }
        if (tier == CustomerTier.PLATINUM) {
            benefits.add("Exclusive member events")
        }

        return "<ul>" + benefits.joinToString("") { "<li>$it</li>" } + "</ul>"
    }

    private fun getBenefitsPlainText(tier: CustomerTier): String {
        val benefits = mutableListOf<String>()

        if (tier.discountPercent > 0) {
            benefits.add("- ${tier.discountPercent}% discount on all orders")
        }
        if (tier.freeShipping) {
            benefits.add("- Free shipping on all orders")
        }
        benefits.add("- Priority customer support")
        if (tier == CustomerTier.GOLD || tier == CustomerTier.PLATINUM) {
            benefits.add("- Early access to new products")
        }
        if (tier == CustomerTier.PLATINUM) {
            benefits.add("- Exclusive member events")
        }

        return benefits.joinToString("\n")
    }
}
