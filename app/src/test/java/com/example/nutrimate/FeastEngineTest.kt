package com.example.nutrimate

import com.example.nutrimate.data.feast.FeastEngine
import org.junit.Assert.*
import org.junit.Test

class FeastEngineTest {

    @Test
    fun testFeastWorkedExampleFromUserPrompt() {
        val P = 700_000L // 7,000 Tk
        val D = 30
        val numFeasts = 2

        val state = FeastEngine.initializeState(
            monthlyBudgetPaisa = P,
            fixedCostsPaisa = 0L,
            daysInMonth = D,
            numFeasts = numFeasts
        )

        // Verify base daily budget b0 = P / D = 23,333
        assertEquals(23_333L, state.baseDailyBudgetPaisa)

        // Verify feast budget f = 2 * b0 = 46,666
        assertEquals(46_666L, state.defaultFeastBudgetPaisa)
        assertEquals(2, state.feasts.size)
        assertEquals(46_666L, state.feasts[0].budgetPaisa)

        // Verify daily budget b = (P - 2 * 46666) / 30 = (700000 - 93332) / 30 = 606668 / 30 = 20,222
        val b = (P - 2 * 46_666L) / 30
        assertEquals(20_222L, b)

        // Lunch allowance A = 40% of b = (20222 * 40) / 100 = 8088
        val lunchAllowance = state.mealAllowancesPaisa["lunch"]!!
        assertEquals(8_088L, lunchAllowance)

        // User asks for kacchi biriyani: X = 12,000 (Tk 120)
        val X = 12_000L
        val proposal = FeastEngine.evaluateRequest(
            state = state,
            mealId = "lunch",
            mealName = "Lunch",
            requestedPricePaisa = X,
            itemName = "Kacchi Biriyani",
            placeName = "Star Kabab & Biriyani",
            kcal = 850
        )

        assertNotNull(proposal)
        // Shortfall s = 12000 - 8088 = 3912
        assertEquals(3_912L, proposal!!.shortfallPaisa)
        assertNotNull(proposal.feastToCancel)
        assertEquals("feast_1", proposal.feastToCancel!!.id)

        // Remainder r = 46666 - 3912 = 42754
        assertEquals(42_754L, proposal.remainderToUnallocatedPaisa)

        // Apply proposal atomically
        val nextState = FeastEngine.applyProposal(state, proposal)

        // Lunch allowance becomes X (12,000)
        assertEquals(12_000L, nextState.mealAllowancesPaisa["lunch"])
        // First feast is cancelled
        assertTrue(nextState.feasts[0].isCancelled)
        // Remainder is credited to unallocated balance U
        assertEquals(proposal.remainderToUnallocatedPaisa + state.unallocatedPaisa, nextState.unallocatedPaisa)

        // Ledger rule strictly holds: sum(all meal allowances) + sum(planned active feast budgets) + U <= P
        assertTrue(nextState.isLedgerValid)
    }
}
