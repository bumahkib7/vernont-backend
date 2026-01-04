package com.vernont.infrastructure.email

import com.mailersend.sdk.MailerSend
import com.mailersend.sdk.MailerSendResponse
import com.mailersend.sdk.emails.Email
import com.mailersend.sdk.exceptions.MailerSendException
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service

@Service
class MailerSendEmailService(
    @Value("\${app.mailersend.token:}") private val apiToken: String,
    @Value("\${app.mail.from:}") private val fromEmail: String,
    @Value("\${app.mail.from-name:Vernont}") private val fromName: String,
) : EmailService {

    private val logger = KotlinLogging.logger {}

    private fun client(): MailerSend {
        if (apiToken.isBlank()) {
            throw EmailException("MailerSend API token not configured (app.mailersend.token)")
        }
        val ms = MailerSend()
        ms.setToken(apiToken)
        return ms
    }

    override suspend fun sendTransactionalEmail(
        to: String,
        subject: String,
        htmlContent: String,
        plainTextContent: String?
    ) {
        sendTransactionalEmailBatch(listOf(to), subject, htmlContent, plainTextContent)
    }

    override suspend fun sendTransactionalEmailBatch(
        recipients: List<String>,
        subject: String,
        htmlContent: String,
        plainTextContent: String?
    ) {
        if (recipients.isEmpty()) return
        val email = Email().apply {
            setFrom(fromName, fromEmail)
            recipients.forEach { addRecipient("", it) }
            setSubject(subject)
            setHtml(htmlContent)
            if (!plainTextContent.isNullOrBlank()) {
                setPlain(plainTextContent)
            }
        }
        try {
            withContext(Dispatchers.IO) {
                val response: MailerSendResponse = client().emails().send(email)
                logger.info { "MailerSend dispatched messageId=${'$'}{response.messageId}" }
            }
        } catch (e: MailerSendException) {
            logger.error(e) { "MailerSend error: ${'$'}{e.message}" }
            throw EmailException("Failed to send email via MailerSend", e)
        } catch (e: Exception) {
            logger.error(e) { "MailerSend unexpected error: ${'$'}{e.message}" }
            throw EmailException("Failed to send email via MailerSend", e)
        }
    }

    override suspend fun sendTemplateEmail(
        to: String,
        templateId: String,
        templateData: Map<String, Any>,
        subject: String?
    ) {
        // Basic transactional path for now; advanced templates can be added later using MailerSend templates API
        val html = buildTemplateHtml(templateId, templateData)
        sendTransactionalEmail(to, subject ?: "", html, null)
    }

    override suspend fun sendTemplateEmailBatch(
        recipients: List<String>,
        templateId: String,
        templateData: Map<String, Any>,
        subject: String?
    ) {
        val html = buildTemplateHtml(templateId, templateData)
        sendTransactionalEmailBatch(recipients, subject ?: "", html, null)
    }

    private fun buildTemplateHtml(templateId: String, templateData: Map<String, Any>): String {
        // Brand-aware HTML email builder
        val brand = (templateData["brand"] as? String) ?: "Vernont"
        val preheader = (templateData["preheader"] as? String) ?: ""
        val title = (templateData["title"] as? String) ?: (templateData["subject"] as? String) ?: brand
        val heading = (templateData["heading"] as? String) ?: title
        val body = (templateData["body"] as? String) ?: ""
        val buttonText = (templateData["buttonText"] as? String) ?: "Open"
        val buttonUrl = (templateData["buttonUrl"] as? String) ?: "#"
        val footerText = (templateData["footer"] as? String) ?: "© ${'$'}brand"
        val logoUrl = (templateData["logoUrl"] as? String) ?: ""

        // Note: Web fonts are inconsistently supported in email clients. We include elegant fallbacks.
        val headingFont = "'Playfair Display', Georgia, 'Times New Roman', serif"
        val bodyFont = "'Inter', 'Helvetica Neue', Arial, sans-serif"

        fun esc(s: String) = s
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&#39;")

        // body can contain trusted HTML; allow basic tags but ensure templateData passes sanitized content upstream when needed.
        val safeHeading = esc(heading)
        val safeTitle = esc(title)
        val safePre = esc(preheader)
        val safeBtnText = esc(buttonText)
        val safeFooter = esc(footerText)

        return """
<!doctype html>
<html lang=\"en\">
  <head>
    <meta name=\"viewport\" content=\"width=device-width, initial-scale=1\" />
    <meta http-equiv=\"Content-Type\" content=\"text/html; charset=UTF-8\" />
    <title>${'$'}safeTitle</title>
    <style>
      /* Base resets */
      body { margin:0; padding:0; background-color:#f6f6f6; color:#111111; }
      img { border:none; -ms-interpolation-mode:bicubic; max-width:100%; }
      table { border-collapse:separate; mso-table-lspace:0pt; mso-table-rspace:0pt; width:100%; }
      table td { font-family:${'$'}bodyFont; font-size:14px; vertical-align:top; }

      /* Container */
      .body { background-color:#f6f6f6; width:100%; }
      .container { display:block; margin:0 auto !important; max-width:640px; padding:24px; width:640px; }
      .content { background:#ffffff; border-radius:12px; overflow:hidden; }

      /* Header */
      .header { padding:24px; text-align:center; background:#ffffff; }
      .brand { font-family:${'$'}headingFont; font-size:20px; letter-spacing:0.5px; }
      .logo { height:28px; }

      /* Hero */
      .hero { padding:32px 32px 8px 32px; text-align:left; }
      .h1 { font-family:${'$'}headingFont; font-weight:600; font-size:28px; line-height:1.25; margin:0; }
      .pre { display:none; line-height:1px; max-height:0; max-width:0; opacity:0; overflow:hidden; mso-hide:all; }

      /* Body */
      .main { padding:8px 32px 24px 32px; color:#111111; font-family:${'$'}bodyFont; font-size:15px; line-height:1.6; }
      .btn-wrap { padding:8px 32px 32px 32px; }
      .btn { display:inline-block; background:#111111; color:#ffffff !important; text-decoration:none; padding:12px 18px; border-radius:8px; font-weight:600; letter-spacing:0.2px; }

      /* Footer */
      .footer { text-align:center; color:#666666; font-size:12px; padding:24px; }
      .muted { color:#888888; }

      /* Card border accent */
      .content { box-shadow: 0 1px 2px rgba(0,0,0,0.04), 0 8px 24px rgba(0,0,0,0.08); }

      @media only screen and (max-width: 640px) {
        .container { padding:12px !important; width:100% !important; }
        .hero { padding:24px !important; }
        .main { padding:8px 24px 24px 24px !important; }
        .btn-wrap { padding:8px 24px 24px 24px !important; }
      }
    </style>
  </head>
  <body>
    <span class=\"pre\">${'$'}safePre</span>
    <table role=\"presentation\" border=\"0\" cellpadding=\"0\" cellspacing=\"0\" class=\"body\">
      <tr>
        <td>&nbsp;</td>
        <td class=\"container\">
          <div class=\"header\">${'$'}{if (logoUrl.isNotBlank()) "<img class=\\\"logo\\\" src=\\\"${'$'}logoUrl\\\" alt=\\\"${'$'}brand\\\" />" else "<div class=\\\"brand\\\">${'$'}brand</div>"}</div>
          <div class=\"content\">
            <div class=\"hero\">
              <h1 class=\"h1\">${'$'}safeHeading</h1>
            </div>
            <div class=\"main\">
              ${'$'}body
            </div>
            <div class=\"btn-wrap\">
              <a class=\"btn\" href=\"${'$'}buttonUrl\">${'$'}safeBtnText</a>
            </div>
          </div>
          <div class=\"footer\">
            <div class=\"muted\">${'$'}safeFooter</div>
          </div>
        </td>
        <td>&nbsp;</td>
      </tr>
    </table>
  </body>
</html>
        """.trimIndent()
    }
}
