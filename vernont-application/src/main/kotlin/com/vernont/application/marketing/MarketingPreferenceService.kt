package com.vernont.application.marketing

import com.vernont.domain.marketing.CampaignType
import com.vernont.domain.marketing.EmailFrequency
import com.vernont.domain.marketing.MarketingPreference
import com.vernont.repository.customer.CustomerRepository
import com.vernont.repository.marketing.MarketingPreferenceRepository
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

private val logger = KotlinLogging.logger {}

@Service
@Transactional
class MarketingPreferenceService(
        private val preferenceRepository: MarketingPreferenceRepository,
        private val customerRepository: CustomerRepository,
        private val emailService: com.vernont.infrastructure.email.EmailService
) {

    fun getOrCreatePreference(customerId: String): MarketingPreference {
        return preferenceRepository.findByCustomerIdAndDeletedAtIsNull(customerId)
                ?: createDefaultPreference(customerId)
    }

    private fun createDefaultPreference(customerId: String): MarketingPreference {
        logger.info { "Creating default marketing preferences for customer: $customerId" }

        val customer =
                customerRepository.findByIdAndDeletedAtIsNull(customerId)
                        ?: throw IllegalArgumentException("Customer not found: $customerId")

        val preference =
                MarketingPreference().apply {
                    this.customer = customer
                    marketingEmailsEnabled = true
                    priceDropAlertsEnabled = true
                    newArrivalsEnabled = true
                    weeklyDigestEnabled = true
                    promotionalEnabled = true
                    emailFrequency = EmailFrequency.NORMAL
                }

        return preferenceRepository.save(preference)
    }

    fun updatePreference(
            customerId: String,
            request: UpdatePreferenceRequest
    ): MarketingPreference {
        logger.info { "Updating marketing preferences for customer: $customerId" }

        val preference = getOrCreatePreference(customerId)

        request.marketingEmailsEnabled?.let { preference.marketingEmailsEnabled = it }
        request.priceDropAlertsEnabled?.let { preference.priceDropAlertsEnabled = it }
        request.newArrivalsEnabled?.let { preference.newArrivalsEnabled = it }
        request.weeklyDigestEnabled?.let { preference.weeklyDigestEnabled = it }
        request.promotionalEnabled?.let { preference.promotionalEnabled = it }
        request.emailFrequency?.let { preference.emailFrequency = it }
        request.preferredCategories?.let { preference.preferredCategories = it.toMutableList() }
        request.preferredBrands?.let { preference.preferredBrands = it.toMutableList() }
        request.excludedCategories?.let { preference.excludedCategories = it.toMutableList() }
        request.excludedBrands?.let { preference.excludedBrands = it.toMutableList() }

        return preferenceRepository.save(preference)
    }

    fun unsubscribe(customerId: String, reason: String? = null) {
        logger.info { "Unsubscribing customer from marketing emails: $customerId" }

        val preference = getOrCreatePreference(customerId)
        preference.unsubscribeAll(reason)
        preferenceRepository.save(preference)

        // Send confirmation email
        sendUnsubscribeConfirmationEmail(preference.customer)
    }

    fun resubscribe(customerId: String) {
        logger.info { "Resubscribing customer to marketing emails: $customerId" }

        val preference = getOrCreatePreference(customerId)
        preference.resubscribe()
        preferenceRepository.save(preference)
    }

    @Transactional(readOnly = true)
    fun canReceiveEmail(customerId: String, campaignType: CampaignType): Boolean {
        val preference =
                preferenceRepository.findByCustomerIdAndDeletedAtIsNull(customerId)
                        ?: return true // Default to true if no preference exists yet

        return preference.canReceiveEmail(campaignType)
    }

    @Transactional(readOnly = true)
    fun getCustomersWithMarketingEnabled(): List<MarketingPreference> {
        return preferenceRepository.findAllWithMarketingEnabled()
    }

    @Transactional(readOnly = true)
    fun getCustomersWithPriceAlertsEnabled(): List<MarketingPreference> {
        return preferenceRepository.findAllWithPriceAlertsEnabled()
    }

    @Transactional(readOnly = true)
    fun getCustomersWithNewArrivalsEnabled(): List<MarketingPreference> {
        return preferenceRepository.findAllWithNewArrivalsEnabled()
    }

    @Transactional(readOnly = true)
    fun getCustomersWithWeeklyDigestEnabled(): List<MarketingPreference> {
        return preferenceRepository.findAllWithWeeklyDigestEnabled()
    }

    private fun sendUnsubscribeConfirmationEmail(
            customer: com.vernont.domain.customer.Customer
    ) {
        val email = customer.getEffectiveEmail()
        val firstName = customer.getEffectiveFirstName() ?: "Customer"
        val subject = "You've been unsubscribed"

        val html =
                """
            <!doctype html>
            <html>
            <head>
              <meta charset='utf-8'>
              <meta name='viewport' content='width=device-width, initial-scale=1'>
              <title>You've been unsubscribed</title>
              <style>
                :root { color-scheme: light dark; supported-color-schemes: light dark; }
                body { margin:0; padding:0; background:#f6f7f9; font-family: -apple-system,BlinkMacSystemFont,'Segoe UI',Roboto,Helvetica,Arial,sans-serif; }
                .container { max-width: 560px; margin: 0 auto; padding: 24px 16px; }
                .card { background:#ffffff; border-radius:12px; padding:28px; box-shadow:0 1px 3px rgba(0,0,0,0.06); }
                h1 { margin:0 0 12px; font-size:22px; line-height:1.3; color:#111827; }
                p { margin:0 0 12px; font-size:14px; color:#374151; }
                .btn { display:inline-block; padding:12px 18px; background:#111827; color:#ffffff !important; text-decoration:none; border-radius:8px; font-weight:600; }
                .muted { color:#6b7280; font-size:12px; }
                .brand { font-weight:800; letter-spacing:0.08em; font-size:12px; color:#111827; text-transform:uppercase; }
                @media (prefers-color-scheme: dark) {
                  body { background:#0b0c0f; }
                  .card { background:#111317; box-shadow:none; }
                  h1 { color:#e5e7eb; }
                  p { color:#cbd5e1; }
                  .brand { color:#cbd5e1; }
                }
              </style>
            </head>
            <body>
              <div class='container'>
                <div class='brand'>VERNONT</div>
                <div class='card'>
                  <h1>We're sad to see you go</h1>
                  <p>Hi $firstName,</p>
                  <p>You have been successfully unsubscribed from our marketing emails. We're sorry to see you go!</p>
                  <p>If you changed your mind or did this by mistake, you can resubscribe at any time.</p>
                  <p style='margin:20px 0;'>
                    <a class='btn' href='http://localhost:3000'>Return to Store</a>
                  </p>
                </div>
                <p class='muted' style='margin-top:12px;'>&copy; ${java.util.Calendar.getInstance().get(java.util.Calendar.YEAR)} Vernont. All rights reserved.</p>
              </div>
            </body>
            </html>
        """.trimIndent()

        // Launch in coroutine scope if needed, but EmailService is suspend function.
        // Since this method is not suspend, we need to adapt.
        // Assuming we are in a transactional context, we might want to trigger this async?
        // But the method signatures in EmailService are 'suspend'.
        // So I must change 'unsubscribe' to runBlocking or make it suspend?
        // Making service methods 'suspend' propagates changes to Controller.
        // Controller methods are NOT suspend currently (fun unsubscribe(...)).
        // But 'runBlocking' inside a Controller/Service is generally discouraged but simpler here
        // than refactoring everything.
        // Better: Use a CoroutineScope to launch it async, fire and forget logic?
        // Or refactor controller to be suspend (Spring WebFlux / MVC supports suspend functions).
        // Let's check Controller again.
        // Controller has `fun getPreferences` (not suspend).
        // publicSubscribeByEmail uses `runBlocking`. I will follow that pattern for now to minimize
        // refactor.

        kotlinx.coroutines.runBlocking {
            try {
                emailService.sendTransactionalEmail(
                        email,
                        subject,
                        html,
                        "You have been unsubscribed."
                )
            } catch (e: Exception) {
                logger.error(e) { "Failed to send unsubscribe confirmation email to $email" }
                // Don't fail the unsubscribe action just because email failed
            }
        }
    }
}

data class UpdatePreferenceRequest(
        val marketingEmailsEnabled: Boolean? = null,
        val priceDropAlertsEnabled: Boolean? = null,
        val newArrivalsEnabled: Boolean? = null,
        val weeklyDigestEnabled: Boolean? = null,
        val promotionalEnabled: Boolean? = null,
        val emailFrequency: EmailFrequency? = null,
        val preferredCategories: List<String>? = null,
        val preferredBrands: List<String>? = null,
        val excludedCategories: List<String>? = null,
        val excludedBrands: List<String>? = null
)
