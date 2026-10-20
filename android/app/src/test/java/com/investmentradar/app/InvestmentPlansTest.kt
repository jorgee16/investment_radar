package com.investmentradar.app

import org.junit.Assert.*
import org.junit.Test

class InvestmentPlansTest {
    @Test fun presetsAreEditableAllocationsNotInventedPurchases() {
        for (key in listOf("world", "europe", "satellite", "reserve")) {
            val plan = createInvestmentPlan(key)
            assertEquals(100.0, plan.groups.sumOf { it.weight.toDouble() }, .0001)
            assertTrue(plan.groups.all { it.items.isEmpty() })
            assertTrue(plan.linkedPositionIds.isEmpty())
        }
        assertTrue(createInvestmentPlan("sectors").groups.isEmpty())
        assertTrue(createInvestmentPlan("custom").groups.isEmpty())
        assertNotEquals(createInvestmentPlan("world").id, createInvestmentPlan("world").id)
    }
}
