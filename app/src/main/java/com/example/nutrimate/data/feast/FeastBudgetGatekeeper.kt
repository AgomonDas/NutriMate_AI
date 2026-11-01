package com.example.nutrimate.data.feast

import android.util.Log
import com.example.nutrimate.data.model.MealItemEntity
import com.example.nutrimate.data.repository.NutriMateFirestoreRepository
import com.example.nutrimate.model.MealSlot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.tasks.await
import java.util.ArrayDeque
import java.util.UUID

private const val TAG = "FeastGatekeeper"

data class UndoSnapshot(
    val state: FeastBudgetState,
    val modifiedMealSlot: MealSlot,
    val explanation: String
)

class FeastBudgetGatekeeper(
    private val firestore: FirebaseFirestore?,
    initialMonthlyBudgetPaisa: Long = 700_000L // Default Tk 7,000
) {
    private val _budgetState = MutableStateFlow(
        FeastEngine.initializeState(monthlyBudgetPaisa = initialMonthlyBudgetPaisa)
    )
    val budgetState: StateFlow<FeastBudgetState> = _budgetState.asStateFlow()

    private val undoStack = ArrayDeque<UndoSnapshot>()
    private val _canUndo = MutableStateFlow(false)
    val canUndo: StateFlow<Boolean> = _canUndo.asStateFlow()

    fun updateMonthlyBudget(newBudgetPaisa: Long) {
        val updated = FeastEngine.initializeState(
            monthlyBudgetPaisa = newBudgetPaisa,
            fixedCostsPaisa = _budgetState.value.fixedCostsPaisa,
            daysInMonth = _budgetState.value.daysInMonth,
            numFeasts = _budgetState.value.feasts.size.coerceAtLeast(2)
        )
        _budgetState.value = updated
        undoStack.clear()
        _canUndo.value = false
    }

    /**
     * Tool function: check()
     * Validates if a requested item fits within the current meal allowance,
     * or if funding exists in unallocated balance U or via feast cancellation.
     */
    fun check(
        mealId: String,
        mealName: String,
        requestedPricePaisa: Long,
        itemName: String,
        placeName: String,
        kcal: Int
    ): TradeoffProposal? {
        return FeastEngine.evaluateRequest(
            state = _budgetState.value,
            mealId = mealId,
            mealName = mealName,
            requestedPricePaisa = requestedPricePaisa,
            itemName = itemName,
            placeName = placeName,
            kcal = kcal
        )
    }

    /**
     * Tool function: update()
     * Atomically executes confirmed trade-off, saves snapshot for undo,
     * and updates the local state and Firestore.
     */
    suspend fun applyTradeoff(
        proposal: TradeoffProposal,
        currentMealSlot: MealSlot,
        userId: String?,
        repository: NutriMateFirestoreRepository?
    ): Boolean {
        return try {
            val currentState = _budgetState.value

            // Push snapshot to undo stack
            undoStack.push(
                UndoSnapshot(
                    state = currentState,
                    modifiedMealSlot = currentMealSlot,
                    explanation = proposal.plainEnglishExplanation
                )
            )
            _canUndo.value = true

            // Apply state transformation
            val nextState = FeastEngine.applyProposal(currentState, proposal)
            _budgetState.value = nextState

            // Sync meal update to user's Firestore table
            if (userId != null && repository != null) {
                repository.saveMeal(
                    userId,
                    MealItemEntity(
                        id = proposal.mealId,
                        name = proposal.mealName,
                        emoji = currentMealSlot.emoji,
                        budget = (proposal.requestedPricePaisa / 100).toInt(),
                        targetKcal = proposal.requestedKcal,
                        activeHourLimit = currentMealSlot.activeHourLimit,
                        selectedPlace = proposal.requestedPlaceName,
                        selectedTitle = proposal.requestedItemName,
                        selectedPrice = (proposal.requestedPricePaisa / 100).toInt(),
                        isPartner = false,
                        discountTag = ""
                    )
                )
            }
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to apply tradeoff", e)
            false
        }
    }

    /**
     * Undo last trade-off action, restoring both meal slot and feast reserve.
     */
    suspend fun undoLastAction(
        userId: String?,
        repository: NutriMateFirestoreRepository?
    ): MealSlot? {
        if (undoStack.isEmpty()) return null
        val snapshot = undoStack.pop()
        _canUndo.value = undoStack.isNotEmpty()

        _budgetState.value = snapshot.state

        // Restore Firestore meal document
        if (userId != null && repository != null) {
            val restored = snapshot.modifiedMealSlot
            val opt = restored.activeOption
            repository.saveMeal(
                userId,
                MealItemEntity(
                    id = restored.id,
                    name = restored.name,
                    emoji = restored.emoji,
                    budget = restored.budget,
                    targetKcal = restored.targetKcal,
                    activeHourLimit = restored.activeHourLimit,
                    selectedPlace = opt.place,
                    selectedTitle = opt.title,
                    selectedPrice = opt.priceNum,
                    isPartner = opt.isPartner,
                    discountTag = opt.discountTag
                )
            )
        }

        return snapshot.modifiedMealSlot
    }

    /**
     * Log a manual custom meal and spent amount.
     * 1. Updates user's personal meals table.
     * 2. Dual-writes to developer community staging collection for curation into central food table.
     */
    suspend fun logManualMealAndSubmitToCommunity(
        userId: String,
        restaurantName: String,
        dishName: String,
        spentTaka: Int,
        calories: Int,
        mealSlotId: String,
        mealSlotName: String,
        emoji: String,
        repository: NutriMateFirestoreRepository?
    ): Boolean {
        val pricePaisa = spentTaka * 100L
        return try {
            // Update user's personal meal
            repository?.saveMeal(
                userId,
                MealItemEntity(
                    id = mealSlotId,
                    name = mealSlotName,
                    emoji = emoji,
                    budget = spentTaka,
                    targetKcal = calories,
                    selectedPlace = restaurantName,
                    selectedTitle = dishName,
                    selectedPrice = spentTaka,
                    isPartner = false,
                    discountTag = ""
                )
            )

            // Submit to community staging table for developer review
            firestore?.let { db ->
                val submissionId = "sub_${UUID.randomUUID()}"
                val submissionData = hashMapOf(
                    "id" to submissionId,
                    "submittedBy" to userId,
                    "restaurantName" to restaurantName,
                    "dishName" to dishName,
                    "pricePaisa" to pricePaisa,
                    "calories" to calories,
                    "mealCategory" to mealSlotId,
                    "status" to "pending",
                    "createdAt" to FieldValue.serverTimestamp()
                )
                db.collection("community_food_submissions")
                    .document(submissionId)
                    .set(submissionData)
                    .await()
            }
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error logging manual meal and community submission", e)
            false
        }
    }
}
