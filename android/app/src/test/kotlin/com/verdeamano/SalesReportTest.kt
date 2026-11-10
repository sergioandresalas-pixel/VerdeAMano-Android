package com.verdeamano

import com.verdeamano.contracts.OrderStatus
import com.verdeamano.contracts.OrderSummary
import com.verdeamano.feature.admin.ordersInPeriod
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant

class SalesReportTest {
    private val now = Instant.parse("2026-11-09T15:00:00Z")

    private fun order(id: String, createdAt: String) =
        OrderSummary(id, OrderStatus.PENDING, 1200, createdAt, createdAt, 1)

    @Test fun periodUsesCreationTimeAndIncludesBoundary() {
        val boundary = order("boundary", "2026-11-02T15:00:00Z")
        val current = order("current", "2026-11-09T14:59:59Z")
        val old = order("old", "2026-11-02T14:59:59Z")
        val future = order("future", "2026-11-09T15:00:01Z")
        val invalid = order("invalid", "")
        assertEquals(listOf(boundary, current), ordersInPeriod(listOf(boundary, current, old, future, invalid), 7, now))
    }

    @Test fun unfilteredPagePreservesOrdersWithUnavailableDates() {
        val orders = listOf(order("invalid", ""), order("old", "2025-11-09T15:00:00Z"))
        assertEquals(orders, ordersInPeriod(orders, 0, now))
    }
}
