package com.vernont.repository.order

import com.vernont.domain.order.OrderGiftCard
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface OrderGiftCardRepository : JpaRepository<OrderGiftCard, String> {

    fun findByOrderId(orderId: String): List<OrderGiftCard>

    fun findByGiftCardId(giftCardId: String): List<OrderGiftCard>

    fun findByGiftCardCode(giftCardCode: String): List<OrderGiftCard>
}
