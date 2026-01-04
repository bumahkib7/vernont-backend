package com.vernont.workflow.scheduler

import com.vernont.workflow.common.WorkflowConstants
import com.vernont.workflow.engine.WorkflowContext
import com.vernont.workflow.engine.WorkflowEngine
import com.vernont.workflow.engine.WorkflowOptions
import com.vernont.workflow.engine.WorkflowResult
import com.vernont.workflow.flows.marketing.*
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.runBlocking
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

private val logger = KotlinLogging.logger {}

@Component
@ConditionalOnProperty(
    prefix = "app.marketing",
    name = ["scheduling.enabled"],
    havingValue = "true",
    matchIfMissing = true
)
class MarketingScheduler(
    private val workflowEngine: WorkflowEngine
) {

    /**
     * Price Drop Alerts - Check every 6 hours
     */
    @Scheduled(cron = "0 0 */6 * * ?")
    fun schedulePriceDropAlerts() = runBlocking {
        logger.info { "Starting scheduled price drop alert check" }

        try {
            val result = workflowEngine.execute(
                workflowName = WorkflowConstants.Marketing.PRICE_DROP_ALERT,
                input = PriceDropAlertInput(checkIntervalHours = 6),
                inputType = PriceDropAlertInput::class,
                outputType = PriceDropAlertOutput::class,
                context = WorkflowContext(),
                options = WorkflowOptions(lockKey = "marketing:price-drop-alerts")
            )

            when (result) {
                is WorkflowResult.Success -> {
                    logger.info { "Price drop alerts completed: ${result.data}" }
                }
                is WorkflowResult.Failure -> {
                    logger.error(result.error) { "Price drop alerts failed" }
                }
            }
        } catch (e: Exception) {
            logger.error(e) { "Scheduled price drop alerts failed" }
        }
    }

    /**
     * Win-back campaigns - Check daily at 9 AM
     */
    @Scheduled(cron = "0 0 9 * * ?")
    fun scheduleWinBackCampaigns() = runBlocking {
        logger.info { "Starting scheduled win-back campaigns" }

        try {
            val result = workflowEngine.execute(
                workflowName = WorkflowConstants.Marketing.WIN_BACK_CAMPAIGN,
                input = WinBackCampaignInput(inactiveDays = listOf(30, 60, 90)),
                inputType = WinBackCampaignInput::class,
                outputType = WinBackCampaignOutput::class,
                context = WorkflowContext(),
                options = WorkflowOptions(lockKey = "marketing:win-back")
            )

            when (result) {
                is WorkflowResult.Success -> {
                    logger.info { "Win-back campaigns completed: ${result.data}" }
                }
                is WorkflowResult.Failure -> {
                    logger.error(result.error) { "Win-back campaigns failed" }
                }
            }
        } catch (e: Exception) {
            logger.error(e) { "Scheduled win-back campaigns failed" }
        }
    }

    /**
     * Weekly Digest - Every Monday at 10 AM
     */
    @Scheduled(cron = "0 0 10 * * MON")
    fun scheduleWeeklyDigest() = runBlocking {
        logger.info { "Starting scheduled weekly digest" }

        try {
            val result = workflowEngine.execute(
                workflowName = WorkflowConstants.Marketing.WEEKLY_DIGEST,
                input = WeeklyDigestInput(maxProducts = 10),
                inputType = WeeklyDigestInput::class,
                outputType = WeeklyDigestOutput::class,
                context = WorkflowContext(),
                options = WorkflowOptions(lockKey = "marketing:weekly-digest")
            )

            when (result) {
                is WorkflowResult.Success -> {
                    logger.info { "Weekly digest completed: ${result.data}" }
                }
                is WorkflowResult.Failure -> {
                    logger.error(result.error) { "Weekly digest failed" }
                }
            }
        } catch (e: Exception) {
            logger.error(e) { "Scheduled weekly digest failed" }
        }
    }

    /**
     * Check for scheduled campaigns every 5 minutes
     * TODO: Implement ScheduledCampaignExecutorWorkflow
     */
    @Scheduled(fixedRate = 300000) // 5 minutes
    fun checkScheduledCampaigns() = runBlocking {
        logger.info { "Checking for scheduled campaigns ready to execute" }
        // Implementation coming soon
    }
}
