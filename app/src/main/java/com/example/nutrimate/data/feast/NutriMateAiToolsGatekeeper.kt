package com.example.nutrimate.data.feast

import android.util.Log
import com.example.nutrimate.data.model.MealItemEntity
import com.example.nutrimate.data.repository.NutriMateFirestoreRepository
import com.example.nutrimate.model.MealSlot
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID

private const val TAG = "NutriMateAiTools"

data class AiMenuItem(
    val itemId: String,
    val name: String,
    val place: String,
    val priceTk: Int,
    val kcal: Int,
    val slot: String,
    val isVegetarian: Boolean = false
)

data class CheckResult(
    val ok: Boolean,
    val priceTk: Int,
    val slotBudgetTk: Int,
    val kcal: Int,
    val checkToken: String? = null,
    val reason: String? = null,
    val shortByTk: Double? = null
)

data class FundingOption(
    val optionId: String,
    val summary: String,
    val token: String,
    val requiresConfirmation: Boolean,
    val shortfallTk: Double,
    val feastTitle: String? = null
)

data class FundingResult(
    val options: List<FundingOption>
)

data class UpdateResult(
    val status: String, // "applied" | "needs_confirmation" | "rejected"
    val message: String? = null,
    val explanation: String? = null,
    val tradeoffToken: String? = null,
    val reason: String? = null
)

data class StoredToken(
    val token: String,
    val slotId: String,
    val item: AiMenuItem,
    val pricePaisa: Long,
    val shortfallPaisa: Long,
    val requiresConfirmation: Boolean,
    val feastToCancel: PlannedFeast?,
    val unallocatedUsedPaisa: Long,
    val summary: String
)

class NutriMateAiToolsGatekeeper(
    private val feastGatekeeper: FeastBudgetGatekeeper
) {
    // In-memory student food catalog across 4 meal slots
    val menuCatalog: List<AiMenuItem> = listOf(
        // Breakfast
        AiMenuItem("paratha_egg", "Paratha, egg, tea", "Dhaka Paratha House", 35, 450, "breakfast", false),
        AiMenuItem("khichuri_combo", "Khichuri combo", "Campus Canteen", 38, 420, "breakfast", true),
        AiMenuItem("tong_toast", "Toast + banana", "Tong Stall", 25, 280, "breakfast", true),
        AiMenuItem("ruti_sabji", "Ruti with mixed vegetable", "Bismillah Hotel", 28, 310, "breakfast", true),
        AiMenuItem("boiled_egg_banana", "2 Boiled eggs & banana", "Tong Stall", 30, 290, "breakfast", false),

        // Lunch
        AiMenuItem("chicken_rice_bowl", "Chicken rice bowl", "Star Kabab & Biriyani", 75, 720, "lunch", false),
        AiMenuItem("kacchi_biriyani", "Kacchi Biriyani", "Star Kabab & Biriyani", 120, 850, "lunch", false),
        AiMenuItem("hall_mess_fish", "Rice, dal, fish", "Hall Mess", 60, 650, "lunch", false),
        AiMenuItem("haji_veg_thali", "Veg thali", "Haji Dhaba", 70, 580, "lunch", true),
        AiMenuItem("beef_bhuna_set", "Beef bhuna set", "Nirala Hotel", 95, 820, "lunch", false),
        AiMenuItem("morog_polao", "Morog Polao", "Dhaka Biriyani House", 110, 810, "lunch", false),
        AiMenuItem("egg_curry_rice", "Egg curry with rice & dal", "Campus Canteen", 50, 560, "lunch", false),

        // Snacks
        AiMenuItem("fuchka_chotpoti", "Fuchka + chotpoti", "Fuchka Corner", 30, 260, "snacks", true),
        AiMenuItem("fruit_cart", "Seasonal fruit cup", "Fruit Cart", 25, 180, "snacks", true),
        AiMenuItem("singara_cha", "Singara (2 pcs) + Cha", "Campus Tong", 20, 290, "snacks", true),
        AiMenuItem("chicken_egg_roll", "Chicken egg roll", "Food Box", 45, 380, "snacks", false),
        AiMenuItem("peanut_cha", "Roasted peanuts + lemon tea", "Campus Tong", 15, 190, "snacks", true),

        // Dinner
        AiMenuItem("tehari_salad", "Tehari + salad", "Biryani Bhaban", 51, 780, "dinner", false),
        AiMenuItem("grilled_chicken_naan", "Grilled chicken quarter + naan", "Rooftop Cafe", 90, 820, "dinner", false),
        AiMenuItem("egg_khichuri", "Egg khichuri", "Street Stall", 40, 620, "dinner", false),
        AiMenuItem("dal_bhat_vorta", "Rice, 3 vortas & thick dal", "Campus Tong", 35, 520, "dinner", true),
        AiMenuItem("chicken_khichuri_hot", "Hot chicken khichuri", "Dhanmondi Tong", 75, 710, "dinner", false)
    )

    private val tokenStore = mutableMapOf<String, StoredToken>()

    fun getStoredToken(token: String): StoredToken? = tokenStore[token]

    /**
     * Tool 1: search_menu(query, slot, max_price_tk)
     * Returns up to 5 matching items {item_id, name, place, price_tk, kcal}.
     */
    fun searchMenu(query: String, slot: String? = null, maxPriceTk: Int? = null): List<AiMenuItem> {
        val q = query.trim().lowercase()
        return menuCatalog
            .filter { item ->
                val matchesQuery = q.isEmpty() ||
                        item.name.lowercase().contains(q) ||
                        item.place.lowercase().contains(q) ||
                        item.itemId.lowercase().contains(q)
                val matchesSlot = slot.isNullOrBlank() || item.slot.equals(slot.trim(), ignoreCase = true)
                val matchesPrice = maxPriceTk == null || item.priceTk <= maxPriceTk
                matchesQuery && matchesSlot && matchesPrice
            }
            .take(5)
    }

    /**
     * Tool 2: check(slot, item_id)
     * Read-only dry run.
     * Returns {ok, price_tk, slot_budget_tk, kcal, check_token} or {ok: false, reason: "over_budget", short_by_tk}.
     */
    fun check(slot: String, itemId: String): CheckResult {
        val item = menuCatalog.find { it.itemId == itemId || it.name.equals(itemId, ignoreCase = true) }
            ?: return CheckResult(
                ok = false,
                priceTk = 0,
                slotBudgetTk = 0,
                kcal = 0,
                reason = "item_not_found"
            )

        val budgetState = feastGatekeeper.budgetState.value
        val normalizedSlot = slot.lowercase().trim()
        val allowancePaisa = budgetState.mealAllowancesPaisa[normalizedSlot] ?: (item.priceTk * 100L)
        val requestedPaisa = item.priceTk * 100L
        val slotBudgetTk = (allowancePaisa / 100).toInt()

        return if (requestedPaisa <= allowancePaisa) {
            val token = "chk_" + UUID.randomUUID().toString().take(12)
            tokenStore[token] = StoredToken(
                token = token,
                slotId = normalizedSlot,
                item = item,
                pricePaisa = requestedPaisa,
                shortfallPaisa = 0L,
                requiresConfirmation = false,
                feastToCancel = null,
                unallocatedUsedPaisa = 0L,
                summary = "Switch $normalizedSlot to ${item.name} (${item.place}) for ৳${item.priceTk}."
            )
            CheckResult(
                ok = true,
                priceTk = item.priceTk,
                slotBudgetTk = slotBudgetTk,
                kcal = item.kcal,
                checkToken = token
            )
        } else {
            val shortfallPaisa = requestedPaisa - allowancePaisa
            val shortfallTk = shortfallPaisa.toDouble() / 100.0
            CheckResult(
                ok = false,
                priceTk = item.priceTk,
                slotBudgetTk = slotBudgetTk,
                kcal = item.kcal,
                reason = "over_budget",
                shortByTk = shortfallTk
            )
        }
    }

    /**
     * Tool 3: find_funding(slot, item_id)
     * Only when over budget. Returns funding options in strict priority order:
     * 1. Unspent monthly balance U (free).
     * 2. Cancelling upcoming planned feast.
     */
    fun findFunding(slot: String, itemId: String): FundingResult {
        val item = menuCatalog.find { it.itemId == itemId || it.name.equals(itemId, ignoreCase = true) }
            ?: return FundingResult(emptyList())

        val budgetState = feastGatekeeper.budgetState.value
        val normalizedSlot = slot.lowercase().trim()
        val allowancePaisa = budgetState.mealAllowancesPaisa[normalizedSlot] ?: 0L
        val requestedPaisa = item.priceTk * 100L
        val shortfallPaisa = (requestedPaisa - allowancePaisa).coerceAtLeast(0L)
        val shortfallTk = shortfallPaisa.toDouble() / 100.0

        val options = mutableListOf<FundingOption>()

        // Option 1: Unallocated buffer U
        if (budgetState.unallocatedPaisa >= shortfallPaisa) {
            val token = "fund_u_" + UUID.randomUUID().toString().take(12)
            val summary = "Use unallocated balance (৳%.2f). Free reserve, no feast lost.".format(shortfallTk)
            tokenStore[token] = StoredToken(
                token = token,
                slotId = normalizedSlot,
                item = item,
                pricePaisa = requestedPaisa,
                shortfallPaisa = shortfallPaisa,
                requiresConfirmation = false,
                feastToCancel = null,
                unallocatedUsedPaisa = shortfallPaisa,
                summary = summary
            )
            options.add(
                FundingOption(
                    optionId = "unallocated",
                    summary = summary,
                    token = token,
                    requiresConfirmation = false,
                    shortfallTk = shortfallTk
                )
            )
        }

        // Option 2: Cancel nearest upcoming feast
        val candidateFeast = budgetState.feasts.firstOrNull { !it.isCancelled && it.budgetPaisa >= shortfallPaisa }
        if (candidateFeast != null) {
            val remainderPaisa = candidateFeast.budgetPaisa - shortfallPaisa
            val token = "fund_f_" + UUID.randomUUID().toString().take(12)
            val summary = "I can pay for this by cancelling ${candidateFeast.title}. You lose the feast. Remainder ৳%.2f returns to your savings.".format(
                remainderPaisa.toDouble() / 100.0
            )
            tokenStore[token] = StoredToken(
                token = token,
                slotId = normalizedSlot,
                item = item,
                pricePaisa = requestedPaisa,
                shortfallPaisa = shortfallPaisa,
                requiresConfirmation = true,
                feastToCancel = candidateFeast,
                unallocatedUsedPaisa = 0L,
                summary = summary
            )
            options.add(
                FundingOption(
                    optionId = "feast_${candidateFeast.id}",
                    summary = summary,
                    token = token,
                    requiresConfirmation = true,
                    shortfallTk = shortfallTk,
                    feastTitle = candidateFeast.title
                )
            )
        }

        return FundingResult(options)
    }

    /**
     * Tool 4: update(token)
     * The only write tool. Needs a signed token from check or find_funding.
     * Returns {status: applied | needs_confirmation | rejected}.
     */
    fun update(
        token: String,
        currentSlots: List<MealSlot>,
        userId: String?,
        repository: NutriMateFirestoreRepository?
    ): UpdateResult {
        val stored = tokenStore[token]
            ?: return UpdateResult(
                status = "rejected",
                reason = "Invalid or expired authorization token."
            )

        if (stored.requiresConfirmation) {
            return UpdateResult(
                status = "needs_confirmation",
                tradeoffToken = token,
                explanation = stored.summary
            )
        }

        // Token is pre-approved (either within budget or using unallocated U)
        val slot = currentSlots.find { it.id == stored.slotId }
        val allowancePaisa = feastGatekeeper.budgetState.value.mealAllowancesPaisa[stored.slotId] ?: 0L
        val proposal = TradeoffProposal(
            mealId = stored.slotId,
            mealName = slot?.name ?: stored.slotId.replaceFirstChar { it.uppercase() },
            currentAllowancePaisa = allowancePaisa,
            requestedItemName = stored.item.name,
            requestedPlaceName = stored.item.place,
            requestedPricePaisa = stored.pricePaisa,
            requestedKcal = stored.item.kcal,
            shortfallPaisa = stored.shortfallPaisa,
            usedUnallocatedPaisa = stored.unallocatedUsedPaisa,
            feastToCancel = stored.feastToCancel,
            remainderToUnallocatedPaisa = if (stored.feastToCancel != null) (stored.feastToCancel.budgetPaisa - stored.shortfallPaisa).coerceAtLeast(0L) else 0L,
            plainEnglishExplanation = stored.summary
        )

        val applied = if (slot != null) {
            // Apply synchronously into gatekeeper state
            try {
                kotlinx.coroutines.runBlocking {
                    feastGatekeeper.applyTradeoff(proposal, slot, userId, repository)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error applying proposal", e)
                false
            }
        } else false

        return if (applied) {
            tokenStore.remove(token)
            UpdateResult(
                status = "applied",
                message = "Plan updated! ${stored.item.name} is scheduled for ${stored.slotId} at ৳${stored.item.priceTk}."
            )
        } else {
            UpdateResult(
                status = "rejected",
                reason = "Ledger validation check failed."
            )
        }
    }

    /**
     * Confirms a feast trade-off that was in needs_confirmation status.
     * Called when the student taps the Confirm button in the chat or dialog.
     */
    suspend fun confirmTradeoff(
        token: String,
        currentSlots: List<MealSlot>,
        userId: String?,
        repository: NutriMateFirestoreRepository?
    ): TradeoffProposal? {
        val stored = tokenStore.remove(token) ?: return null
        val slot = currentSlots.find { it.id == stored.slotId } ?: return null
        val allowancePaisa = feastGatekeeper.budgetState.value.mealAllowancesPaisa[stored.slotId] ?: 0L

        val proposal = TradeoffProposal(
            mealId = stored.slotId,
            mealName = slot.name,
            currentAllowancePaisa = allowancePaisa,
            requestedItemName = stored.item.name,
            requestedPlaceName = stored.item.place,
            requestedPricePaisa = stored.pricePaisa,
            requestedKcal = stored.item.kcal,
            shortfallPaisa = stored.shortfallPaisa,
            usedUnallocatedPaisa = stored.unallocatedUsedPaisa,
            feastToCancel = stored.feastToCancel,
            remainderToUnallocatedPaisa = if (stored.feastToCancel != null) (stored.feastToCancel.budgetPaisa - stored.shortfallPaisa).coerceAtLeast(0L) else 0L,
            plainEnglishExplanation = stored.summary
        )

        val success = feastGatekeeper.applyTradeoff(proposal, slot, userId, repository)
        return if (success) proposal else null
    }

    /**
     * Tool 5: get_alternatives(slot, max_price_tk)
     * Returns 2-3 similar cheaper items.
     */
    fun getAlternatives(slot: String, maxPriceTk: Int? = null): List<AiMenuItem> {
        val normalizedSlot = slot.lowercase().trim()
        val budgetState = feastGatekeeper.budgetState.value
        val allowanceTk = ((budgetState.mealAllowancesPaisa[normalizedSlot] ?: 8000L) / 100).toInt()
        val limit = maxPriceTk ?: allowanceTk

        return menuCatalog
            .filter { it.slot.equals(normalizedSlot, ignoreCase = true) && it.priceTk <= limit }
            .take(3)
    }

    /**
     * Generates the token-efficient compact context string for Gemini prompt.
     */
    fun generateCompactContext(
        isVegetarian: Boolean,
        dateString: String
    ): String {
        val state = feastGatekeeper.budgetState.value
        val nextFeast = state.feasts.firstOrNull { !it.isCancelled }
        val feastInfo = if (nextFeast != null) {
            "${nextFeast.title} (${FeastEngine.formatTaka(nextFeast.budgetPaisa)}, active)"
        } else "None (all repurposed)"

        val bPaisa = state.mealAllowancesPaisa["breakfast"] ?: 4044L
        val lPaisa = state.mealAllowancesPaisa["lunch"] ?: 8088L
        val sPaisa = state.mealAllowancesPaisa["snacks"] ?: 3033L
        val dPaisa = state.mealAllowancesPaisa["dinner"] ?: 5055L

        return buildString {
            append("Today: $dateString. ")
            append("Pool: ${FeastEngine.formatTaka(state.poolPaisa)}. ")
            append("Unallocated balance U: ${FeastEngine.formatTaka(state.unallocatedPaisa)}. ")
            append("Meal allowances: Breakfast ${FeastEngine.formatTaka(bPaisa)}, Lunch ${FeastEngine.formatTaka(lPaisa)}, Snacks ${FeastEngine.formatTaka(sPaisa)}, Dinner ${FeastEngine.formatTaka(dPaisa)}. ")
            append("Next Feast: $feastInfo. ")
            append("Diet: ${if (isVegetarian) "Vegetarian" else "Non-vegetarian"}.")
        }
    }
}
