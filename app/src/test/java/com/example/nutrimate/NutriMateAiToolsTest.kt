package com.example.nutrimate

import com.example.nutrimate.data.feast.*
import com.example.nutrimate.model.MealOption
import com.example.nutrimate.model.MealSlot
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class NutriMateAiToolsTest {

    private lateinit var feastGatekeeper: FeastBudgetGatekeeper
    private lateinit var aiToolsGatekeeper: NutriMateAiToolsGatekeeper
    private lateinit var sampleSlots: List<MealSlot>

    @Before
    fun setUp() {
        feastGatekeeper = FeastBudgetGatekeeper(null, initialMonthlyBudgetPaisa = 700_000L)
        aiToolsGatekeeper = NutriMateAiToolsGatekeeper(feastGatekeeper)

        sampleSlots = listOf(
            MealSlot(
                id = "breakfast",
                name = "Breakfast",
                emoji = "🍳",
                budget = 35,
                targetKcal = 450,
                activeHourLimit = 11,
                options = listOf(MealOption("Dhaka Paratha House", "Paratha, egg, tea", "৳35", 35))
            ),
            MealSlot(
                id = "lunch",
                name = "Lunch",
                emoji = "🍛",
                budget = 81,
                targetKcal = 800,
                activeHourLimit = 16,
                options = listOf(MealOption("Star Kabab & Biriyani", "Chicken rice bowl", "৳75", 75))
            ),
            MealSlot(
                id = "snacks",
                name = "Snacks",
                emoji = "🥪",
                budget = 30,
                targetKcal = 260,
                activeHourLimit = 19,
                options = listOf(MealOption("Fuchka Corner", "Fuchka + chotpoti", "৳30", 30))
            ),
            MealSlot(
                id = "dinner",
                name = "Dinner",
                emoji = "🍲",
                budget = 51,
                targetKcal = 800,
                activeHourLimit = 24,
                options = listOf(MealOption("Biryani Bhaban", "Tehari + salad", "৳51", 51))
            )
        )
    }

    @Test
    fun testSearchMenuReturnsMatchingItems() {
        val results = aiToolsGatekeeper.searchMenu(query = "biriyani", slot = "lunch")
        assertFalse(results.isEmpty())
        val kacchi = results.find { it.itemId == "kacchi_biriyani" }
        assertNotNull(kacchi)
        assertEquals(120, kacchi?.priceTk)
        assertEquals("lunch", kacchi?.slot)
    }

    @Test
    fun testCheckAffordableMealFitsAllowance() {
        val checkRes = aiToolsGatekeeper.check(slot = "lunch", itemId = "chicken_rice_bowl")
        assertTrue("Chicken rice bowl (Tk 75) should fit in lunch allowance (Tk 80.88)", checkRes.ok)
        assertNotNull("Should produce a signed check_token", checkRes.checkToken)
        assertNull("Should not have over_budget reason", checkRes.reason)

        // Directly updating with this check token should succeed immediately
        val updateRes = aiToolsGatekeeper.update(
            token = checkRes.checkToken!!,
            currentSlots = sampleSlots,
            userId = null,
            repository = null
        )
        assertEquals("applied", updateRes.status)
    }

    @Test
    fun testCheckOverBudgetTriggersFeastTradeoffFlow() = runBlocking {
        // Step 1: Check Kacchi Biriyani (Tk 120 > allowance Tk 80.88)
        val checkRes = aiToolsGatekeeper.check(slot = "lunch", itemId = "kacchi_biriyani")
        assertFalse("Kacchi Biriyani should not fit in standard lunch allowance", checkRes.ok)
        assertEquals("over_budget", checkRes.reason)
        assertEquals(39.12, checkRes.shortByTk ?: 0.0, 0.01)

        // Step 2: find_funding
        val fundingRes = aiToolsGatekeeper.findFunding(slot = "lunch", itemId = "kacchi_biriyani")
        assertFalse("Funding options should be returned", fundingRes.options.isEmpty())
        val feastOption = fundingRes.options.first()
        assertTrue("Feast cancellation must require user confirmation", feastOption.requiresConfirmation)

        // Step 3: update(token) without user confirmation -> status must be needs_confirmation
        val updateRes = aiToolsGatekeeper.update(
            token = feastOption.token,
            currentSlots = sampleSlots,
            userId = null,
            repository = null
        )
        assertEquals("needs_confirmation", updateRes.status)
        assertEquals(feastOption.token, updateRes.tradeoffToken)

        // Step 4: confirm trade-off
        val proposal = aiToolsGatekeeper.confirmTradeoff(
            token = feastOption.token,
            currentSlots = sampleSlots,
            userId = null,
            repository = null
        )
        assertNotNull("Confirmed tradeoff must produce applied proposal", proposal)
        assertEquals("Kacchi Biriyani", proposal?.requestedItemName)

        // Step 5: Verify post-state in gatekeeper
        val nextState = feastGatekeeper.budgetState.value
        val cancelledFeast = nextState.feasts.find { it.isCancelled }
        assertNotNull("Nearest planned feast should be cancelled", cancelledFeast)
        assertTrue("Remainder should be transferred to unallocated buffer U", nextState.unallocatedPaisa > 0)

        // Step 6: Verify ledger rule holds strictly
        assertTrue("Ledger rule must hold strictly after feast cancellation", nextState.isLedgerValid)
    }

    @Test
    fun testGetAlternativesReturnsCheaperOptions() {
        val alternatives = aiToolsGatekeeper.getAlternatives(slot = "lunch", maxPriceTk = 70)
        assertFalse("Should return cheaper lunch options", alternatives.isEmpty())
        assertTrue("All alternatives must be <= Tk 70", alternatives.all { it.priceTk <= 70 })
    }
}
