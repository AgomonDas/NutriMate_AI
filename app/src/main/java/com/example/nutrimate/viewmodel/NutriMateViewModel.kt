package com.example.nutrimate.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.nutrimate.model.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.Calendar
import java.util.UUID

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
    val totalSavedFromDiscounts: Int = 680
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

class NutriMateViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(NutriMateUiState())
    val uiState: StateFlow<NutriMateUiState> = _uiState.asStateFlow()

    init {
        initializeMeals()
        initializeMonthlyBars()
        initializeInitialChat()
    }

    private fun initializeMeals() {
        val currentHour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        val initialMeals = listOf(
            MealSlot(
                id = "breakfast",
                name = "Breakfast",
                emoji = "🍳",
                budget = 40,
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
                budget = 80,
                targetKcal = 800,
                activeHourLimit = 16,
                options = listOf(
                    MealOption("Star Kabab & Biriyani", "Chicken rice bowl", "৳75", 75, true, "−10%"),
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
                budget = 50,
                targetKcal = 800,
                activeHourLimit = 24,
                options = listOf(
                    MealOption("Biryani Bhaban", "Tehari + salad", "৳55", 55, true, "−10%"),
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
            content = "Hey Dhruv! Tell me what you're craving or ask anything about food. I can swap meals in your plan too."
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

    fun selectMealOption(slotIndex: Int, optionIndex: Int) {
        _uiState.update { state ->
            val updatedSlots = state.mealSlots.toMutableList()
            if (slotIndex in updatedSlots.indices) {
                val slot = updatedSlots[slotIndex]
                if (optionIndex in slot.options.indices) {
                    val opt = slot.options[optionIndex]
                    updatedSlots[slotIndex] = slot.copy(
                        selectedOptionIndex = optionIndex,
                        budget = opt.priceNum
                    )
                }
            }
            state.copy(mealSlots = updatedSlots)
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

        val lower = userText.lowercase()

        viewModelScope.launch {
            if (lower.contains("biriyani") || lower.contains("biryani") || lower.contains("swap") || lower.contains("craving")) {
                delay(600)
                val checkCall = ChatMessage(
                    id = UUID.randomUUID().toString(),
                    sender = MessageSender.FUNCTION_CALL,
                    content = "check(meal=\"lunch\", item=\"biriyani\") → feasible · ৳75 ≤ ৳80, 790 kcal"
                )
                _uiState.update { it.copy(chatMessages = it.chatMessages + checkCall) }

                delay(900)
                val reply = ChatMessage(
                    id = UUID.randomUUID().toString(),
                    sender = MessageSender.ASSISTANT,
                    content = "Biriyani fits your lunch budget and calories! Updating your plan to Star Kabab Chicken Biriyani…"
                )
                // Update lunch slot option 0
                _uiState.update { state ->
                    val updatedSlots = state.mealSlots.map { slot ->
                        if (slot.id == "lunch") {
                            val newOptions = slot.options.toMutableList()
                            newOptions[0] = MealOption("Star Kabab & Biriyani", "Chicken biriyani", "৳75", 75, true, "−10%")
                            slot.copy(options = newOptions, targetKcal = 790, selectedOptionIndex = 0)
                        } else slot
                    }
                    state.copy(chatMessages = state.chatMessages + reply, mealSlots = updatedSlots)
                }

                delay(700)
                val updateCall = ChatMessage(
                    id = UUID.randomUUID().toString(),
                    sender = MessageSender.FUNCTION_CALL,
                    content = "update(day=\"today\", meal=\"lunch\", item=\"biriyani\") → monthly table updated ✓"
                )
                _uiState.update { it.copy(chatMessages = it.chatMessages + updateCall) }

            } else if (lower.contains("cheap") || lower.contains("save") || lower.contains("money")) {
                delay(700)
                val reply = ChatMessage(
                    id = UUID.randomUUID().toString(),
                    sender = MessageSender.ASSISTANT,
                    content = "Swapping dinner to Street Stall egg khichuri saves ৳15 and keeps you right at 780 kcal. I've updated your dinner slot!"
                )
                _uiState.update { state ->
                    val updatedSlots = state.mealSlots.map { slot ->
                        if (slot.id == "dinner") {
                            slot.copy(selectedOptionIndex = 2, budget = 40)
                        } else slot
                    }
                    state.copy(chatMessages = state.chatMessages + reply, mealSlots = updatedSlots)
                }

            } else if (lower.contains("protein") || lower.contains("shake") || lower.contains("gym")) {
                delay(800)
                val reply = ChatMessage(
                    id = UUID.randomUUID().toString(),
                    sender = MessageSender.ASSISTANT,
                    content = "A commercial whey protein shake costs around ৳90 per serving. Eggs and yellow lentils (dal) provide 10g protein for only ৳12 in Dhaka, making them 7.5x more cost-efficient on your ৳6,000 student budget!"
                )
                _uiState.update { it.copy(chatMessages = it.chatMessages + reply) }

            } else {
                delay(700)
                val reply = ChatMessage(
                    id = UUID.randomUUID().toString(),
                    sender = MessageSender.ASSISTANT,
                    content = "I reviewed your daily food intake. Today's meals provide optimal macros for your ${uiState.value.userGoals.dailyCalorieGoal} kcal target while staying within your daily budget limit of ৳${uiState.value.totalTodayBudget}."
                )
                _uiState.update { it.copy(chatMessages = it.chatMessages + reply) }
            }
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
