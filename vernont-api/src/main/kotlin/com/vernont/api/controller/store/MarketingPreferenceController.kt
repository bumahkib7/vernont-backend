package com.vernont.api.controller.store

import com.vernont.application.marketing.MarketingPreferenceService
import com.vernont.application.marketing.UpdatePreferenceRequest
import com.vernont.domain.marketing.EmailFrequency
import com.vernont.domain.marketing.MarketingPreference
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.beans.factory.annotation.Value
import com.vernont.infrastructure.email.EmailService
import io.jsonwebtoken.Jwts
import io.jsonwebtoken.security.Keys
import java.util.*
import kotlinx.coroutines.runBlocking
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/store/marketing/preferences")
@Tag(name = "Marketing Preferences", description = "Customer marketing preference management")
class MarketingPreferenceController(
    private val preferenceService: MarketingPreferenceService,
    private val customerService: com.vernont.application.customer.CustomerService,
    private val emailService: EmailService,
    @Value("\${app.jwt.secret}") private val jwtSecret: String,
    @Value("\${app.frontend.url:http://localhost:3000}") private val frontendUrl: String,
    @Value("\${app.marketing.optin.expiration-ms:86400000}") private val optInExpirationMs: Long,
    @Value("\${app.email.templates.confirm-subscription-id:}") private val confirmTemplateId: String
) {

    @GetMapping("/{customerId}")
    @Operation(summary = "Get customer marketing preferences")
    fun getPreferences(@PathVariable customerId: String): ResponseEntity<MarketingPreferenceResponse> {
        val preferences = preferenceService.getOrCreatePreference(customerId)
        return ResponseEntity.ok(MarketingPreferenceResponse.from(preferences))
    }

    @PatchMapping("/{customerId}")
    @Operation(summary = "Update customer marketing preferences")
    fun updatePreferences(
        @PathVariable customerId: String,
        @RequestBody request: UpdatePreferenceRequest
    ): ResponseEntity<MarketingPreferenceResponse> {
        val updated = preferenceService.updatePreference(customerId, request)
        return ResponseEntity.ok(MarketingPreferenceResponse.from(updated))
    }

    @PostMapping("/{customerId}/unsubscribe")
    @Operation(summary = "Unsubscribe from all marketing emails")
    fun unsubscribe(
        @PathVariable customerId: String,
        @RequestBody(required = false) request: UnsubscribeRequest?
    ): ResponseEntity<Unit> {
        preferenceService.unsubscribe(customerId, request?.reason)
        return ResponseEntity.ok().build()
    }

    @PostMapping("/{customerId}/resubscribe")
    @Operation(summary = "Resubscribe to marketing emails")
    fun resubscribe(@PathVariable customerId: String): ResponseEntity<MarketingPreferenceResponse> {
        preferenceService.resubscribe(customerId)
        val preferences = preferenceService.getOrCreatePreference(customerId)
        return ResponseEntity.ok(MarketingPreferenceResponse.from(preferences))
    }

    @PostMapping("/public/subscribe-email")
   @Operation(summary = "Public: Subscribe to marketing emails by email (anonymous)")
   fun publicSubscribeByEmail(
       @RequestBody req: PublicSubscribeRequest
   ): ResponseEntity<Map<String, String>> {
       val email = req.email.trim()
       val first = (req.firstName ?: "Subscriber").ifBlank { "Subscriber" }
       val last = (req.lastName ?: "User").ifBlank { "User" }

       // Build double opt-in token
       val now = Date()
       val expiry = Date(now.time + optInExpirationMs)
       val key = try {
           val decoded = Base64.getDecoder().decode(jwtSecret.trim())
           Keys.hmacShaKeyFor(decoded)
       } catch (_: Exception) {
           val bytes = jwtSecret.toByteArray()
           require(bytes.size >= 32) { "JWT secret must be at least 256 bits" }
           Keys.hmacShaKeyFor(bytes)
       }
       val token = Jwts.builder()
           .subject(email)
           .issuedAt(now)
           .expiration(expiry)
           .claim("purpose", "marketing_opt_in")
           .claim("firstName", first)
           .claim("lastName", last)
           .signWith(key)
           .compact()

       val apiBase = System.getenv("API_URL") ?: "http://localhost:8080"
       val confirmUrl = "$apiBase/store/marketing/preferences/public/confirm?token=$token"

       // Send confirmation email using branded template if configured
       val subject = req.subject ?: "Confirm your email — Vernont"
       val data = mapOf(
           // Required template variables provided by user
           "home_url" to (req.homeUrl ?: frontendUrl),
           "overline_label" to (req.overlineLabel ?: "VERNONT"),
           "headline" to (req.headline ?: "Confirm your email to unlock curated drops"),
           "cta_label" to (req.ctaLabel ?: "Confirm Email"),
           "confirm_url" to confirmUrl,
           "support_email" to (req.supportEmail ?: "support@vernont.com"),
           "instagram_url" to (req.instagramUrl ?: ""),
           "twitter_url" to (req.twitterUrl ?: ""),
           "facebook_url" to (req.facebookUrl ?: ""),
           "footer_html" to (req.footerHtml ?: "&copy; ${Calendar.getInstance().get(Calendar.YEAR)} Vernont. All rights reserved.<br/>You're receiving this because you signed up for Vernont updates."),
           "brand_name" to (req.brandName ?: "VERNONT")
       )
       runBlocking {
           if (confirmTemplateId.isNotBlank()) {
               emailService.sendTemplateEmail(email, confirmTemplateId, data, subject)
           } else {
               val html = """
                   <!doctype html>
                   <html>
                   <head>
                     <meta charset='utf-8'>
                     <meta name='viewport' content='width=device-width, initial-scale=1'>
                     <title>Confirm your email — Vernont</title>
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
                         <h1>Confirm your subscription</h1>
                         <p>Please confirm your email to start receiving curated updates.</p>
                         <p style='margin:20px 0;'>
                           <a class='btn' href='$confirmUrl'>Confirm subscription</a>
                         </p>
                         <p class='muted'>If the button doesn't work, copy and paste this link into your browser:</p>
                         <p class='muted' style='word-break:break-all;'>$confirmUrl</p>
                       </div>
                       <p class='muted' style='margin-top:12px;'>&copy; ${Calendar.getInstance().get(Calendar.YEAR)} Vernont. All rights reserved.</p>
                     </div>
                   </body>
                   </html>
               """.trimIndent()
               emailService.sendTransactionalEmail(email, subject, html, "Confirm: $confirmUrl")
           }
       }

       return ResponseEntity.accepted().body(mapOf("status" to "pending_confirmation"))
   }
    @GetMapping("/public/confirm")
   @Operation(summary = "Public: Confirm marketing email subscription")
   fun confirmPublicSubscription(@RequestParam token: String): ResponseEntity<Unit> {
       val key = try {
           val decoded = Base64.getDecoder().decode(jwtSecret.trim())
           Keys.hmacShaKeyFor(decoded)
       } catch (_: Exception) {
           val bytes = jwtSecret.toByteArray()
           require(bytes.size >= 32) { "JWT secret must be at least 256 bits" }
           Keys.hmacShaKeyFor(bytes)
       }
       val claims = Jwts.parser().verifyWith(key).build().parseSignedClaims(token).payload
       val purpose = claims["purpose"] as? String
       if (purpose != "marketing_opt_in") {
           return ResponseEntity.status(400).build()
       }
       val email = claims.subject
       val first = (claims["firstName"] as? String) ?: "Subscriber"
       val last = (claims["lastName"] as? String) ?: "User"

       val customer = try {
           customerService.getCustomerByEmail(email)
       } catch (e: Exception) {
           val register = com.vernont.domain.customer.dto.RegisterCustomerRequest(
               email = email,
               firstName = first,
               lastName = last,
               phone = null,
               hasAccount = false
           )
           customerService.registerCustomer(register)
       }

       preferenceService.updatePreference(
           customer.id,
           UpdatePreferenceRequest(
               marketingEmailsEnabled = true,
               priceDropAlertsEnabled = null,
               newArrivalsEnabled = null,
               weeklyDigestEnabled = null,
               promotionalEnabled = null,
               emailFrequency = null,
               preferredCategories = null,
               preferredBrands = null,
               excludedCategories = null,
               excludedBrands = null
           )
       )
       preferenceService.resubscribe(customer.id)

       val redirect = "$frontendUrl/subscribe/confirmed"
       return ResponseEntity.status(302).header("Location", redirect).build()
   }
}

data class MarketingPreferenceResponse(
    val id: String,
    val customerId: String,
    val marketingEmailsEnabled: Boolean,
    val priceDropAlertsEnabled: Boolean,
    val newArrivalsEnabled: Boolean,
    val weeklyDigestEnabled: Boolean,
    val promotionalEnabled: Boolean,
    val emailFrequency: EmailFrequency,
    val preferredCategories: List<String>?,
    val preferredBrands: List<String>?,
    val excludedCategories: List<String>?,
    val excludedBrands: List<String>?,
    val unsubscribedAt: String?,
    val unsubscribeReason: String?
) {
    companion object {
        fun from(preference: MarketingPreference) = MarketingPreferenceResponse(
            id = preference.id,
            customerId = preference.customer.id,
            marketingEmailsEnabled = preference.marketingEmailsEnabled,
            priceDropAlertsEnabled = preference.priceDropAlertsEnabled,
            newArrivalsEnabled = preference.newArrivalsEnabled,
            weeklyDigestEnabled = preference.weeklyDigestEnabled,
            promotionalEnabled = preference.promotionalEnabled,
            emailFrequency = preference.emailFrequency,
            preferredCategories = preference.preferredCategories,
            preferredBrands = preference.preferredBrands,
            excludedCategories = preference.excludedCategories,
            excludedBrands = preference.excludedBrands,
            unsubscribedAt = preference.unsubscribedAt?.toString(),
            unsubscribeReason = preference.unsubscribeReason
        )
    }
}

data class UnsubscribeRequest(
    val reason: String?
)

data class PublicSubscribeRequest(
   val email: String,
   val firstName: String? = null,
   val lastName: String? = null,
   // Optional overrides for branded template variables
   val homeUrl: String? = null,
   val overlineLabel: String? = null,
   val headline: String? = null,
   val ctaLabel: String? = null,
   val supportEmail: String? = null,
   val instagramUrl: String? = null,
   val twitterUrl: String? = null,
   val facebookUrl: String? = null,
   val footerHtml: String? = null,
   val brandName: String? = null,
   val subject: String? = null
)
