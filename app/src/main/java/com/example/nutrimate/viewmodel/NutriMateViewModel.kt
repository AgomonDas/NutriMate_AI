package com.example.nutrimate.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.nutrimate.data.feast.*
import com.example.nutrimate.data.model.MealItemEntity
import com.example.nutrimate.data.model.PaymentRecordEntity
import com.example.nutrimate.data.model.UserProfileEntity
import com.example.nutrimate.data.repository.NutriMateFirestoreRepository
import com.example.nutrimate.model.*
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.UUID

private const val TAG = "NutriMateVM"

data class NutriMateUiState(
    val onboardingCompleted: Boolean = true,
    val onboardingStep: Int = 1,
    val currentTab: AppNavTab = AppNavTab.TODAY,
    val userGoals: UserGoals = UserGoals(),
    val mealSlots: List<MealSlot> = emptyList(),
    val expandedMealIndex: Int = 1,
    val chatMessages: List<ChatMessage> = emptyList(),
    val isChatOpen: Boolean = false,
    val monthlySpends: List<MonthlyDaySpend> = emptyList(),
    val isScanning: Boolean = false,
    val scanFoundPartner: Boolean = false,
    val scanBillAmount: Int = 450,
    val scanPaidSuccess: Boolean = false,
    val scanPaymentMethod: String = "",
    val totalSpentThisMonth: Int = 2800,
    val totalSavedFromDiscounts: Int = 680,
    val isSyncingFirestore: Boolean = false,
    val activeTradeoffProposal: TradeoffProposal? = null,
    val showManualMealDialog: Boolean = false,
    val undoNoticeMessage: String? = null
) {
    val totalTodayBudget: Int
        get() = mealSlots.sumOf { it.budget }

    val totalTodayKcal: Int
        get() = mealSlots.sumOf { it.targetKcal }

    val scanDiscountAmount: Int
        get() = (scanBillAmount * 0.10).toInt()

    val scanNetPayable: Int
        get() = scanBillAmount - scanDiscountAmount
}

class NutriMateViewModel(
    private val repository: NutriMateFirestoreRepository? = null,
    private val currentUserId: String? = null,
    private val gatekeeper: FeastBudgetGatekeeper = FeastBudgetGatekeeper(null)
) : ViewModel() {

    private val _uiState = MutableStateFlow(NutriMateUiState())
    val uiState: StateFlow<NutriMateUiState> = _uiState.asStateFlow()

    val feastState: StateFlow<FeastBudgetState> = gatekeeper.budgetState
    val canUndo: StateFlow<Boolean> = gatekeeper.canUndo

    val aiToolsGatekeeper = NutriMateAiToolsGatekeeper(gatekeeper)
    val chatEngine = NutriMateChatEngine(aiToolsGatekeeper)

    init {
        initializeMeals()
        initializeMonthlyBars()
        initializeInitialChat()

        if (currentUserId != null && repository != null) {
            connectToFirestore(currentUserId)
        }
    }

    private fun connectToFirestore(userId: String) {
        val repo = repository ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isSyncingFirestore = true) }
            val displayName = Firebase.auth.currentUser?.displayName ?: "Dhruv"
            repo.seedInitialDataIfEmpty(userId, displayName)

            // Listen to User Profile
            launch {
                repo.observeUserProfile(userId).collect { profileEntity ->
                    if (profileEntity != null) {
                        _uiState.update { state ->
                            state.copy(
                                userGoals = state.userGoals.copy(
                                    name = profileEntity.name,
                                    phone = profileEntity.phone,
                                    city = profileEntity.city,
                                    age = profileEntity.age,
                                    heightCm = profileEntity.heightCm,
                                    weightKg = profileEntity.weightKg,
                                    isVegetarian = profileEntity.isVegetarian,
                                    allowLocation = profileEntity.allowLocation,
                                    goesToGym = profileEntity.goesToGym,
                                    isStudent = profileEntity.isStudent,
                                    university = profileEntity.university,
                                    monthlyBudget = profileEntity.monthlyBudget,
                                    onMessPlan = profileEntity.onMessPlan,
                                    dailyCalorieGoal = profileEntity.dailyCalorieGoal
                                ),
                                totalSpentThisMonth = profileEntity.totalSpentThisMonth,
                                totalSavedFromDiscounts = profileEntity.totalSavedFromDiscounts,
                                isSyncingFirestore = false
                            )
                        }
                        gatekeeper.updateMonthlyBudget(profileEntity.monthlyBudget * 100L)
                    }
                }
            }

            // Listen to Meals subcollection
            launch {
                repo.observeMeals(userId).collect { mealsList ->
                    if (mealsList.isNotEmpty()) {
                        _uiState.update { state ->
                            val updatedSlots = state.mealSlots.map { slot ->
                                val matched = mealsList.find { it.id == slot.id }
                                if (matched != null) {
                                    slot.copy(
                                        budget = matched.budget,
                                        targetKcal = matched.targetKcal
                                    )
                                } else {
                                    slot
                                }
                            }
                            state.copy(mealSlots = updatedSlots)
                        }
                    }
                }
            }
        }
    }

    private fun initializeMeals() {
        val currentHour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        val initialMeals = listOf(
            MealSlot(
                id = "breakfast",
                name = "Breakfast",
                emoji = "🍳",
                budget = 35,
                targetKcal = 450,
                activeHourLimit = 11,
                options = listOf(
                    MealOption("Dhaka Paratha House", "Paratha, egg, tea", "৳35", 35, true, "−10%"),
                    MealOption("Campus Canteen", "Khichuri combo", "৳38", 38),
                    MealOption("Tong Stall", "Toast + banana", "৳25", 25)
                )
            ),
            MealSlot(
                id = "lunch",
                name = "Lunch",
                emoji = "🍛",
                budget = 81,
                targetKcal = 800,
                activeHourLimit = 16,
                options = listOf(
                    MealOption("Star Kabab & Biriyani", "Chicken rice bowl", "৳75", 75, true, "−10%"),
                    MealOption("Star Kabab & Biriyani", "Kacchi Biriyani (Feast Upgrade)", "৳120", 120, true, "−10%"),
                    MealOption("Hall Mess", "Rice, dal, fish", "৳60", 60),
                    MealOption("Haji Dhaba", "Veg thali", "৳70", 70),
                    MealOption("Nirala Hotel", "Beef bhuna set", "৳95", 95)
                )
            ),
            MealSlot(
                id = "snacks",
                name = "Snacks",
                emoji = "🥪",
                budget = 30,
                targetKcal = 260,
                activeHourLimit = 19,
                options = listOf(
                    MealOption("Fuchka Corner", "Fuchka + chotpoti", "৳30", 30, true, "−5%"),
                    MealOption("Fruit Cart", "Seasonal fruit", "৳25", 25)
                )
            ),
            MealSlot(
                id = "dinner",
                name = "Dinner",
                emoji = "🍲",
                budget = 51,
                targetKcal = 800,
                activeHourLimit = 24,
                options = listOf(
                    MealOption("Biryani Bhaban", "Tehari + salad", "৳51", 51, true, "−10%"),
                    MealOption("Rooftop Cafe", "Grilled chicken", "৳90", 90),
                    MealOption("Street Stall", "Egg khichuri", "৳40", 40)
                )
            )
        )

        val defaultExpanded = initialMeals.indexOfFirst { currentHour < it.activeHourLimit }.coerceAtLeast(0)

        _uiState.update {
            it.copy(
                mealSlots = initialMeals,
                expandedMealIndex = defaultExpanded
            )
        }
    }

    private fun initializeMonthlyBars() {
        val currentDay = Calendar.getInstance().get(Calendar.DAY_OF_MONTH)
        val bars = (1..31).map { day ->
            val pseudoSpend = 140 + ((day * 37) % 85)
            MonthlyDaySpend(
                day = day,
                spent = pseudoSpend,
                isCurrentDay = (day == currentDay)
            )
        }
        _uiState.update { it.copy(monthlySpends = bars) }
    }

    private fun initializeInitialChat() {
        val greeting = ChatMessage(
            id = UUID.randomUUID().toString(),
            sender = MessageSender.ASSISTANT,
            content = "Hey! I'm your Feast & Nutrition Copilot. Tell me what you're craving. If it exceeds your meal allowance, I can check our unallocated reserves or offer to rearrange an upcoming Feast!"
        )
        _uiState.update { it.copy(chatMessages = listOf(greeting)) }
    }

    fun setOnboardingStep(step: Int) {
        _uiState.update { it.copy(onboardingStep = step) }
    }

    fun completeOnboarding() {
        _uiState.update {
            it.copy(
                onboardingCompleted = true,
                currentTab = AppNavTab.TODAY
            )
        }
        syncGoalsToFirestore()
    }

    fun restartOnboarding() {
        _uiState.update {
            it.copy(
                onboardingCompleted = false,
                onboardingStep = 1
            )
        }
    }

    fun updateGoals(transform: (UserGoals) -> UserGoals) {
        _uiState.update {
            val updated = transform(it.userGoals)
            it.copy(userGoals = updated)
        }
        gatekeeper.updateMonthlyBudget(_uiState.value.userGoals.monthlyBudget * 100L)
        syncGoalsToFirestore()
    }

    private fun syncGoalsToFirestore() {
        val uid = currentUserId ?: return
        val repo = repository ?: return
        val goals = _uiState.value.userGoals
        viewModelScope.launch {
            try {
                repo.saveUserProfile(
                    UserProfileEntity(
                        userId = uid,
                        name = goals.name,
                        phone = goals.phone,
                        city = goals.city,
                        age = goals.age,
                        heightCm = goals.heightCm,
                        weightKg = goals.weightKg,
                        isVegetarian = goals.isVegetarian,
                        allowLocation = goals.allowLocation,
                        goesToGym = goals.goesToGym,
                        isStudent = goals.isStudent,
                        university = goals.university,
                        monthlyBudget = goals.monthlyBudget,
                        totalSpentThisMonth = _uiState.value.totalSpentThisMonth,
                        totalSavedFromDiscounts = _uiState.value.totalSavedFromDiscounts,
                        unallocatedPaisa = feastState.value.unallocatedPaisa,
                        onMessPlan = goals.onMessPlan,
                        dailyCalorieGoal = goals.dailyCalorieGoal
                    )
                )
            } catch (e: Exception) {
                Log.e(TAG, "Failed to sync goals to Firestore", e)
            }
        }
    }

    fun selectTab(tab: AppNavTab) {
        if (tab == AppNavTab.CHAT) {
            _uiState.update { it.copy(isChatOpen = true) }
        } else if (tab == AppNavTab.SCAN) {
            startScan()
        } else {
            _uiState.update { it.copy(currentTab = tab, isChatOpen = false) }
        }
    }

    fun toggleMealExpansion(index: Int) {
        _uiState.update {
            it.copy(expandedMealIndex = if (it.expandedMealIndex == index) -1 else index)
        }
    }

    /**
     * Request an upgrade or custom meal through the deterministic gatekeeper.
     */
    fun requestMealUpgrade(
        mealId: String,
        newPricePaisa: Long,
        itemName: String,
        placeName: String,
        kcal: Int
    ) {
        val slot = _uiState.value.mealSlots.find { it.id == mealId } ?: return
        val proposal = gatekeeper.check(
            mealId = mealId,
            mealName = slot.name,
            requestedPricePaisa = newPricePaisa,
            itemName = itemName,
            placeName = placeName,
            kcal = kcal
        )

        if (proposal != null) {
            if (proposal.shortfallPaisa == 0L) {
                // Directly affordable: apply immediately
                viewModelScope.launch {
                    gatekeeper.applyTradeoff(proposal, slot, currentUserId, repository)
                    updateSlotOptionDirectly(mealId, itemName, placeName, (newPricePaisa / 100).toInt(), kcal)
                }
            } else {
                // Requires shortfall funding: present trade-off dialog to user
                _uiState.update { it.copy(activeTradeoffProposal = proposal) }
            }
        }
    }

    fun confirmTradeoffProposal() {
        val proposal = _uiState.value.activeTradeoffProposal ?: return
        val slot = _uiState.value.mealSlots.find { it.id == proposal.mealId } ?: return

        viewModelScope.launch {
            val success = gatekeeper.applyTradeoff(proposal, slot, currentUserId, repository)
            if (success) {
                updateSlotOptionDirectly(
                    slotId = proposal.mealId,
                    itemName = proposal.requestedItemName,
                    placeName = proposal.requestedPlaceName,
                    priceTaka = (proposal.requestedPricePaisa / 100).toInt(),
                    kcal = proposal.requestedKcal
                )
                _uiState.update {
                    it.copy(
                        activeTradeoffProposal = null,
                        undoNoticeMessage = "Upgraded to ${proposal.requestedItemName}! Feast cancelled and remainder saved."
                    )
                }
            }
        }
    }

    fun declineTradeoffProposal() {
        _uiState.update { it.copy(activeTradeoffProposal = null) }
    }

    fun undoLastTradeoff() {
        viewModelScope.launch {
            val restoredSlot = gatekeeper.undoLastAction(currentUserId, repository)
            if (restoredSlot != null) {
                _uiState.update { state ->
                    val updatedSlots = state.mealSlots.map { if (it.id == restoredSlot.id) restoredSlot else it }
                    state.copy(
                        mealSlots = updatedSlots,
                        undoNoticeMessage = "Trade-off undone! Meal and Feast budget restored."
                    )
                }
            }
        }
    }

    fun dismissUndoNotice() {
        _uiState.update { it.copy(undoNoticeMessage = null) }
    }

    fun openManualMealDialog() {
        _uiState.update { it.copy(showManualMealDialog = true) }
    }

    fun dismissManualMealDialog() {
        _uiState.update { it.copy(showManualMealDialog = false) }
    }

    fun logManualMeal(
        restaurant: String,
        dish: String,
        spentTaka: Int,
        kcal: Int,
        slotId: String
    ) {
        val slot = _uiState.value.mealSlots.find { it.id == slotId } ?: return
        viewModelScope.launch {
            val uid = currentUserId ?: "dhruv_dev"
            val success = gatekeeper.logManualMealAndSubmitToCommunity(
                userId = uid,
                restaurantName = restaurant,
                dishName = dish,
                spentTaka = spentTaka,
                calories = kcal,
                mealSlotId = slotId,
                mealSlotName = slot.name,
                emoji = slot.emoji,
                repository = repository
            )
            if (success) {
                updateSlotOptionDirectly(slotId, dish, restaurant, spentTaka, kcal)
                _uiState.update {
                    it.copy(
                        showManualMealDialog = false,
                        undoNoticeMessage = "Logged $dish (৳$spentTaka)! Submitted to community staging."
                    )
                }
            }
        }
    }

    private fun updateSlotOptionDirectly(
        slotId: String,
        itemName: String,
        placeName: String,
        priceTaka: Int,
        kcal: Int
    ) {
        _uiState.update { state ->
            val updatedSlots = state.mealSlots.map { slot ->
                if (slot.id == slotId) {
                    val newOpt = MealOption(placeName, itemName, "৳$priceTaka", priceTaka)
                    val newOpts = listOf(newOpt) + slot.options.filter { it.title != itemName }
                    slot.copy(
                        budget = priceTaka,
                        targetKcal = kcal,
                        options = newOpts,
                        selectedOptionIndex = 0
                    )
                } else slot
            }
            state.copy(mealSlots = updatedSlots)
        }
    }

    fun selectMealOption(slotIndex: Int, optionIndex: Int) {
        val slot = _uiState.value.mealSlots.getOrNull(slotIndex) ?: return
        val opt = slot.options.getOrNull(optionIndex) ?: return

        // Check if item exceeds current allowance
        val currentAllowancePaisa = feastState.value.mealAllowancesPaisa[slot.id] ?: (slot.budget * 100L)
        val requestedPricePaisa = opt.priceNum * 100L

        if (requestedPricePaisa > currentAllowancePaisa) {
            requestMealUpgrade(
                mealId = slot.id,
                newPricePaisa = requestedPricePaisa,
                itemName = opt.title,
                placeName = opt.place,
                kcal = slot.targetKcal
            )
        } else {
            _uiState.update { state ->
                val updatedSlots = state.mealSlots.toMutableList()
                updatedSlots[slotIndex] = slot.copy(
                    selectedOptionIndex = optionIndex,
                    budget = opt.priceNum
                )
                state.copy(mealSlots = updatedSlots)
            }
        }
    }

    fun openChat() {
        _uiState.update { it.copy(isChatOpen = true) }
    }

    fun closeChat() {
        _uiState.update { it.copy(isChatOpen = false) }
    }

    fun sendChatMessage(userText: String) {
        if (userText.isBlank()) return
        val userMsg = ChatMessage(
            id = UUID.randomUUID().toString(),
            sender = MessageSender.USER,
            content = userText
        )
        _uiState.update { it.copy(chatMessages = it.chatMessages + userMsg) }

        viewModelScope.launch {
            try {
                val dateStr = SimpleDateFormat("EEEE, MMMM d", Locale.getDefault()).format(Calendar.getInstance().time)
                val finalMsg = chatEngine.processUserMessage(
                    userText = userText,
                    currentSlots = _uiState.value.mealSlots,
                    isVegetarian = _uiState.value.userGoals.isVegetarian,
                    dateString = dateStr,
                    userId = currentUserId,
                    repository = repository,
                    onStepGenerated = { stepMsg ->
                        _uiState.update { it.copy(chatMessages = it.chatMessages + stepMsg) }
                    }
                )

                if (finalMsg.isPendingConfirmation && finalMsg.tradeoffToken != null) {
                    val candidateFeast = feastState.value.feasts.firstOrNull { !it.isCancelled }
                    val lunchAllowance = feastState.value.mealAllowancesPaisa["lunch"] ?: 8_088L
                    val proposal = TradeoffProposal(
                        mealId = "lunch",
                        mealName = "Lunch",
                        currentAllowancePaisa = lunchAllowance,
                        requestedItemName = "Kacchi Biriyani",
                        requestedPlaceName = "Star Kabab & Biriyani",
                        requestedPricePaisa = 12_000L,
                        requestedKcal = 850,
                        shortfallPaisa = 3_912L,
                        usedUnallocatedPaisa = 0L,
                        feastToCancel = candidateFeast,
                        remainderToUnallocatedPaisa = if (candidateFeast != null) (candidateFeast.budgetPaisa - 3_912L).coerceAtLeast(0L) else 0L,
                        plainEnglishExplanation = finalMsg.content
                    )
                    _uiState.update { it.copy(activeTradeoffProposal = proposal) }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Chat engine error", e)
                val errorMsg = ChatMessage(
                    id = UUID.randomUUID().toString(),
                    sender = MessageSender.ASSISTANT,
                    content = "Sorry, I had trouble processing that request. Please try again."
                )
                _uiState.update { it.copy(chatMessages = it.chatMessages + errorMsg) }
            }
        }
    }

    fun confirmInChatTradeoff(token: String) {
        viewModelScope.launch {
            val proposal = chatEngine.toolsGatekeeper.confirmTradeoff(
                token = token,
                currentSlots = _uiState.value.mealSlots,
                userId = currentUserId,
                repository = repository
            )
            if (proposal != null) {
                updateSlotOptionDirectly(
                    slotId = proposal.mealId,
                    itemName = proposal.requestedItemName,
                    placeName = proposal.requestedPlaceName,
                    priceTaka = (proposal.requestedPricePaisa / 100).toInt(),
                    kcal = proposal.requestedKcal
                )
                val confirmBadge = ChatMessage(
                    id = UUID.randomUUID().toString(),
                    sender = MessageSender.FUNCTION_CALL,
                    content = "gatekeeper.confirm(token=\"${token.take(8)}...\")",
                    toolName = "gatekeeper.confirm",
                    toolArgs = "token=\"${token.take(8)}...\"",
                    toolResult = "Feast cancelled, remainder returned to U, ${proposal.mealId} allowance updated"
                )
                val confirmReply = ChatMessage(
                    id = UUID.randomUUID().toString(),
                    sender = MessageSender.ASSISTANT,
                    content = "Trade-off confirmed! ${proposal.requestedItemName} is scheduled for ${proposal.mealName}. Friday's feast has been cancelled and the remaining savings returned to your buffer."
                )
                _uiState.update { state ->
                    val updatedMessages = state.chatMessages.map {
                        if (it.tradeoffToken == token) it.copy(isPendingConfirmation = false) else it
                    } + confirmBadge + confirmReply
                    state.copy(
                        chatMessages = updatedMessages,
                        activeTradeoffProposal = null,
                        undoNoticeMessage = "Trade-off applied: ${proposal.requestedItemName} for ${proposal.mealName}."
                    )
                }
            }
        }
    }

    fun declineInChatTradeoff(token: String) {
        val alternatives = chatEngine.toolsGatekeeper.getAlternatives("lunch")
        val altText = alternatives.joinToString { "${it.name} (৳${it.priceTk})" }
        val cancelReply = ChatMessage(
            id = UUID.randomUUID().toString(),
            sender = MessageSender.ASSISTANT,
            content = "Trade-off declined. Your feast remains scheduled! Here are delicious options within today's allowance: $altText."
        )
        _uiState.update { state ->
            val updatedMessages = state.chatMessages.map {
                if (it.tradeoffToken == token) it.copy(isPendingConfirmation = false) else it
            } + cancelReply
            state.copy(
                chatMessages = updatedMessages,
                activeTradeoffProposal = null
            )
        }
    }

    fun startScan() {
        _uiState.update {
            it.copy(
                isScanning = true,
                scanFoundPartner = false,
                scanPaidSuccess = false,
                scanPaymentMethod = ""
            )
        }
    }

    fun simulatePartnerFound() {
        _uiState.update {
            it.copy(scanFoundPartner = true)
        }
    }

    fun updateScanBillAmount(amount: Int) {
        _uiState.update {
            it.copy(scanBillAmount = amount.coerceAtLeast(0))
        }
    }

    fun completePayment(method: String) {
        val payable = _uiState.value.scanNetPayable
        val saved = _uiState.value.scanDiscountAmount
        _uiState.update {
            it.copy(
                scanPaidSuccess = true,
                scanPaymentMethod = method,
                totalSpentThisMonth = it.totalSpentThisMonth + payable,
                totalSavedFromDiscounts = it.totalSavedFromDiscounts + saved
            )
        }

        currentUserId?.let { uid ->
            repository?.let { repo ->
                viewModelScope.launch {
                    try {
                        val trxId = "TXN_${UUID.randomUUID().toString().take(8).uppercase()}"
                        repo.recordPayment(
                            uid,
                            PaymentRecordEntity(
                                id = "pay_${System.currentTimeMillis()}",
                                trxId = trxId,
                                vendorName = "Star Kabab & Biriyani",
                                billAmount = _uiState.value.scanBillAmount,
                                discountAmount = saved,
                                netPayable = payable,
                                paymentMethod = method
                            )
                        )
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to record payment in Firestore", e)
                    }
                }
            }
        }
    }

    fun dismissScan() {
        _uiState.update {
            it.copy(
                isScanning = false,
                scanFoundPartner = false,
                scanPaidSuccess = false,
                scanPaymentMethod = ""
            )
        }
    }
}
