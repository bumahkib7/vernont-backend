package com.vernont.domain.customer

import com.vernont.domain.auth.User
import com.vernont.domain.common.BaseEntity
import jakarta.persistence.*

@Entity
@Table(
    name = "user_size_preference",
    uniqueConstraints = [
        UniqueConstraint(name = "uk_user_size_preference_user_size", columnNames = ["user_id", "size"])
    ],
    indexes = [
        Index(name = "idx_user_size_preference_user", columnList = "user_id, deleted_at"),
        Index(name = "idx_user_size_preference_size", columnList = "size, deleted_at")
    ]
)
class UserSizePreference : BaseEntity() {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    lateinit var user: User

    @Column(name = "size", nullable = false)
    var size: String = ""
}
