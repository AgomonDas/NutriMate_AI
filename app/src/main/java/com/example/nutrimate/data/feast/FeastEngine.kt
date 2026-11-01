package com.example.nutrimate.data.feast

/**
 * Pure domain logic for Feast Reserve Budgeting.
 * All monetary amounts are in integer paisa (1 Taka = 100 paisa).
 */
data class PlannedFeast(
    val id: String,
    val title: String,
    val dateLabel: String,
    val budgetPaisa: Long,
    val isCancelled: Boolean = false
)

data class FeastBudgetState(
    val monthlyBudgetPaisa: Long = 700_000L, // Tk 7,000.00
    val fixedCostsPaisa: Long = 0L,
    val daysInMonth: Int = 30,
    val feasts: List<PlannedFeast> = emptyList(),
    val mealAllowancesPaisa: Map<String, Long> = emptyMap(), // breakfast, lunch, snacks, dinner
    val unallocatedPaisa: Long = 0L,
    val minFeastSizePaisa: Long = 10_000L // Tk 100.00 minimum for partial feast
) {
    val poolPaisa: Long
        get() = (monthlyBudgetPaisa - fixedCostsPaisa).coerceAtLeast(0L)

    val baseDailyBudgetPaisa: Long
        get() = if (daysInMonth > 0) poolPaisa / daysInMonth else 0L

    val defaultFeastBudgetPaisa: Long
        get() = 2 * baseDailyBudgetPaisa

    val activeFeastsBudgetSum: Long
        get() = feasts.filter { !it.isCancelled }.sumOf { it.budgetPaisa }

    val totalMealAllowancesSum: Long
        get() = mealAllowancesPaisa.values.sum()

    /**
     * Ledger rule: sum(all meal allowances) + sum(planned active feast budgets) + U <= P
     */
    val isLedgerValid: Boolean
        get() {
            val allocatedSum = totalMealAllowancesSum + activeFeastsBudgetSum + unallocatedPaisa
            return allocatedSum <= poolPaisa
        }
}

data class TradeoffProposal(
    val mealId: String,
    val mealName: String,
    val currentAllowancePaisa: Long,
    val requestedItemName: String,
    val requestedPlaceName: String,
    val requestedPricePaisa: Long,
    val requestedKcal: Int,
    val shortfallPaisa: Long,
    val usedUnallocatedPaisa: Long,
    val feastToCancel: PlannedFeast?,
    val remainderToUnallocatedPaisa: Long,
    val plainEnglishExplanation: String
)

object FeastEngine {

    /**
     * Initializes default budget buckets for a month with n feasts.
     */
    fun initializeState(
        monthlyBudgetPaisa: Long = 700_000L, // Tk 7,000
        fixedCostsPaisa: Long = 0L,
        daysInMonth: Int = 30,
        numFeasts: Int = 2
    ): FeastBudgetState {
        val P = (monthlyBudgetPaisa - fixedCostsPaisa).coerceAtLeast(0L)
        val b0 = if (daysInMonth > 0) P / daysInMonth else 0L
        val f = 2 * b0

        val initialFeasts = (1..numFeasts).map { idx ->
            PlannedFeast(
                id = "feast_$idx",
                title = "Friday Feast #$idx",
                dateLabel = if (idx == 1) "Week 2 Friday" else "Week 4 Friday",
                budgetPaisa = f,
                isCancelled = false
            )
        }

        val totalFeastsBudget = numFeasts * f
        val b = if (daysInMonth > 0) (P - totalFeastsBudget) / daysInMonth else 0L

        // Split daily budget: breakfast 20%, lunch 40%, snacks 15%, dinner 25%
        val breakfast = (b * 20L) / 100L
        val lunch = (b * 40L) / 100L
        val snacks = (b * 15L) / 100L
        val dinner = b - (breakfast + lunch + snacks) // exact conservation

        val allowances = mapOf(
            "breakfast" to breakfast,
            "lunch" to lunch,
            "snacks" to snacks,
            "dinner" to dinner
        )

        val totalAllocated = (allowances.values.sum() * daysInMonth) + totalFeastsBudget
        val U = (P - totalAllocated).coerceAtLeast(0L)

        return FeastBudgetState(
            monthlyBudgetPaisa = monthlyBudgetPaisa,
            fixedCostsPaisa = fixedCostsPaisa,
            daysInMonth = daysInMonth,
            feasts = initialFeasts,
            mealAllowancesPaisa = allowances,
            unallocatedPaisa = U
        )
    }

    /**
     * Evaluates a requested item against the current meal allowance and feast reserves.
     */
    fun evaluateRequest(
        state: FeastBudgetState,
        mealId: String,
        mealName: String,
        requestedPricePaisa: Long,
        itemName: String,
        placeName: String,
        kcal: Int
    ): TradeoffProposal? {
        val currentAllowance = state.mealAllowancesPaisa[mealId] ?: return null
        if (requestedPricePaisa <= currentAllowance) {
            // Fits directly within current allowance
            return TradeoffProposal(
                mealId = mealId,
                mealName = mealName,
                currentAllowancePaisa = currentAllowance,
                requestedItemName = itemName,
                requestedPlaceName = placeName,
                requestedPricePaisa = requestedPricePaisa,
                requestedKcal = kcal,
                shortfallPaisa = 0L,
                usedUnallocatedPaisa = 0L,
                feastToCancel = null,
                remainderToUnallocatedPaisa = 0L,
                plainEnglishExplanation = "$itemName fits your $mealName allowance of ${formatTaka(currentAllowance)}."
            )
        }

        val shortfall = requestedPricePaisa - currentAllowance

        // Step 3a: Check unallocated balance U
        if (state.unallocatedPaisa >= shortfall) {
            return TradeoffProposal(
                mealId = mealId,
                mealName = mealName,
                currentAllowancePaisa = currentAllowance,
                requestedItemName = itemName,
                requestedPlaceName = placeName,
                requestedPricePaisa = requestedPricePaisa,
                requestedKcal = kcal,
                shortfallPaisa = shortfall,
                usedUnallocatedPaisa = shortfall,
                feastToCancel = null,
                remainderToUnallocatedPaisa = 0L,
                plainEnglishExplanation = "$itemName exceeds allowance by ${formatTaka(shortfall)}. It can be funded directly from your unallocated reserve without cancelling any feast."
            )
        }

        // Step 3b: Find an upcoming active planned feast
        val candidateFeast = state.feasts.firstOrNull { !it.isCancelled && it.budgetPaisa >= shortfall }
        if (candidateFeast != null) {
            val remainder = candidateFeast.budgetPaisa - shortfall
            val explanation = "I can pay for this by cancelling ${candidateFeast.title}. You lose the feast, and the remaining ${formatTaka(remainder)} returns to your unallocated balance."

            return TradeoffProposal(
                mealId = mealId,
                mealName = mealName,
                currentAllowancePaisa = currentAllowance,
                requestedItemName = itemName,
                requestedPlaceName = placeName,
                requestedPricePaisa = requestedPricePaisa,
                requestedKcal = kcal,
                shortfallPaisa = shortfall,
                usedUnallocatedPaisa = 0L,
                feastToCancel = candidateFeast,
                remainderToUnallocatedPaisa = remainder,
                plainEnglishExplanation = explanation
            )
        }

        // If no single feast is enough, check all remaining feasts
        return null // Cannot fund
    }

    /**
     * Atomically applies a confirmed trade-off proposal to state.
     */
    fun applyProposal(
        state: FeastBudgetState,
        proposal: TradeoffProposal
    ): FeastBudgetState {
        val updatedAllowances = state.mealAllowancesPaisa.toMutableMap()
        updatedAllowances[proposal.mealId] = proposal.requestedPricePaisa

        var updatedFeasts = state.feasts
        var updatedUnallocated = state.unallocatedPaisa

        if (proposal.usedUnallocatedPaisa > 0) {
            updatedUnallocated -= proposal.usedUnallocatedPaisa
        } else if (proposal.feastToCancel != null) {
            updatedFeasts = state.feasts.map { f ->
                if (f.id == proposal.feastToCancel.id) {
                    f.copy(isCancelled = true)
                } else f
            }
            updatedUnallocated += proposal.remainderToUnallocatedPaisa
        }

        val newState = state.copy(
            feasts = updatedFeasts,
            mealAllowancesPaisa = updatedAllowances,
            unallocatedPaisa = updatedUnallocated
        )

        // Strict ledger rule check
        check(newState.isLedgerValid) { "Ledger rule violation after applying trade-off!" }
        return newState
    }

    fun formatTaka(paisa: Long): String {
        val taka = paisa / 100.0
        return if (paisa % 100 == 0L) {
            "৳%,d".format(paisa / 100)
        } else {
            "৳%,.2f".format(taka)
        }
    }
}
