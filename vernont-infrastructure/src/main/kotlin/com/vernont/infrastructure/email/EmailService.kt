package com.vernont.infrastructure.email

/**
 * Interface for email service implementations.
 * Provides abstraction for sending transactional and template-based emails.
 */
interface EmailService {

    /**
     * Send a transactional email to a single recipient.
     *
     * @param to Recipient email address
     * @param subject Email subject
     * @param htmlContent HTML content of the email
     * @param plainTextContent Optional plain text version of the email
     * @throws EmailException if the email fails to send
     */
    suspend fun sendTransactionalEmail(
        to: String,
        subject: String,
        htmlContent: String,
        plainTextContent: String? = null
    )

    /**
     * Send a transactional email to multiple recipients.
     *
     * @param recipients List of recipient email addresses
     * @param subject Email subject
     * @param htmlContent HTML content of the email
     * @param plainTextContent Optional plain text version of the email
     * @throws EmailException if the email fails to send
     */
    suspend fun sendTransactionalEmailBatch(
        recipients: List<String>,
        subject: String,
        htmlContent: String,
        plainTextContent: String? = null
    )

    /**
     * Send an email using a template.
     *
     * @param to Recipient email address
     * @param templateId SendGrid template ID
     * @param templateData Dynamic template data as a map
     * @param subject Optional email subject (if not provided by template)
     * @throws EmailException if the email fails to send
     */
    suspend fun sendTemplateEmail(
        to: String,
        templateId: String,
        templateData: Map<String, Any>,
        subject: String? = null
    )

    /**
     * Send a template email to multiple recipients.
     *
     * @param recipients List of recipient email addresses
     * @param templateId SendGrid template ID
     * @param templateData Dynamic template data as a map
     * @param subject Optional email subject (if not provided by template)
     * @throws EmailException if the email fails to send
     */
    suspend fun sendTemplateEmailBatch(
        recipients: List<String>,
        templateId: String,
        templateData: Map<String, Any>,
        subject: String? = null
    )
}

/**
 * Custom exception for email service errors.
 */
class EmailException(message: String, cause: Throwable? = null) : Exception(message, cause)
