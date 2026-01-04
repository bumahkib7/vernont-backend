package com.vernont.repository.marketing

import com.vernont.domain.marketing.ActivityType
import com.vernont.domain.marketing.UserActivityLog
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository
import java.time.Instant

@Repository
interface UserActivityLogRepository : JpaRepository<UserActivityLog, String> {

    fun findByUserIdOrderByCreatedAtDesc(userId: String): List<UserActivityLog>

    @Query("""
        SELECT ual FROM UserActivityLog ual
        WHERE ual.userId = :userId
        AND ual.activityType = :activityType
        AND ual.createdAt >= :since
        ORDER BY ual.createdAt DESC
    """)
    fun findByUserIdAndActivityTypeSince(
        userId: String,
        activityType: ActivityType,
        since: Instant
    ): List<UserActivityLog>

    @Query("""
        SELECT ual.userId, MAX(ual.createdAt) as lastActivity
        FROM UserActivityLog ual
        GROUP BY ual.userId
        HAVING MAX(ual.createdAt) < :threshold
    """)
    fun findInactiveUsersSince(threshold: Instant): List<Array<Any>>
}
