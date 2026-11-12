package com.example.nutrimate.viewmodel

import android.app.Activity
import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.nutrimate.data.feast.*
import com.example.nutrimate.data.location.CampusArea
import com.example.nutrimate.data.location.CampusLocationManager
import com.example.nutrimate.data.model.CentralFoodEntity
import com.example.nutrimate.data.model.MealItemEntity
import com.example.nutrimate.data.model.PartnerRestaurantEntity
import com.example.nutrimate.data.model.PaymentRecordEntity
import com.example.nutrimate.data.model.UserProfileEntity
import com.example.nutrimate.data.repository.NutriMateFirestoreRepository
import com.example.nutrimate.model.*
import com.google.firebase.Firebase
import com.google.firebase.FirebaseException
import com.google.firebase.auth.PhoneAuthCredential
import com.google.firebase.auth.PhoneAuthOptions
import com.google.firebase.auth.PhoneAuthProvider
import com.google.firebase.auth.auth
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.UUID
import java.util.concurrent.TimeUnit

private const val TAG = "NutriMateVM"

data class NutriMateUiState(
    val onboardingCompleted: Boolean = false,
    val isCheckingOnboarding: Boolean = true,
    val onboardingStep: Int = 1,
    val currentTab: AppNavTab = AppNavTab.HOME,
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
    val currentCampusArea: CampusArea = CampusLocationManager.getDefaultArea(),
    val currentLatitude: Double = 23.7266,
    val currentLongitude: Double = 90.3925,
    val nearbyRestaurantsWithDistance: List<com.example.nutrimate.data.model.RestaurantWithDistance> = emptyList(),
    val nearbyPartnerRestaurants: List<PartnerRestaurantEntity> = CampusLocationManager.getDefaultArea().defaultRestaurants,
    val nearbyCentralFoods: List<CentralFoodEntity> = CampusLocationManager.getDefaultArea().defaultMeals,
    val showCampusSelectorDialog: Boolean = false,
    val isLocatingGps: Boolean = false,
    val undoNoticeMessage: String? = null,
    val isAiThinking: Boolean = false,
    val isSendingOtp: Boolean = false,
    val phoneAuthStatus: String? = null,
    val phoneVerificationId: String? = null
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

    @Volatile
    private var sessionOnboardingCompleted: Boolean = false
    @Volatile
    private var hasLoadedInitialProfile: Boolean = false

    init {
        initializeMeals()
        initializeMonthlyBars()
        initializeInitialChat()

        if (currentUserId != null && repository != null) {
            connectToFirestore(currentUserId)
        } else {
            _uiState.update { it.copy(isCheckingOnboarding = false) }
        }
    }

    private fun connectToFirestore(userId: String) {
        val repo = repository ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isSyncingFirestore = true) }
            val displayName = Firebase.auth.currentUser?.displayName ?: "Student"
            val activeArea = _uiState.value.currentCampusArea

            // Safety timeout so onboarding check never hangs indefinitely if offline
            launch {
                delay(3_500L)
                if (_uiState.value.isCheckingOnboarding) {
                    _uiState.update { it.copy(isCheckingOnboarding = false, isSyncingFirestore = false) }
                }
            }

            withTimeoutOrNull(4_000L) {
                repo.seedInitialDataIfEmpty(userId, displayName, activeArea)
            }

            // Listen to User Profile
            launch {
                repo.observeUserProfile(userId).collect { profileEntity ->
                    if (profileEntity != null) {
                        val isDone = profileEntity.onboardingCompleted || sessionOnboardingCompleted
                        _uiState.update { state ->
                            // Only overwrite userGoals on first load or once onboarding is completed
                            // so active typing in onboarding never flickers or snaps back!
                            val updatedGoals = if (!hasLoadedInitialProfile || isDone) {
                                state.userGoals.copy(
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
                                )
                            } else {
                                state.userGoals
                            }
                            hasLoadedInitialProfile = true
                            state.copy(
                                userGoals = updatedGoals,
                                totalSpentThisMonth = profileEntity.totalSpentThisMonth,
                                totalSavedFromDiscounts = profileEntity.totalSavedFromDiscounts,
                                onboardingCompleted = isDone,
                                isCheckingOnboarding = false,
                                isSyncingFirestore = false
                            )
                        }
                        gatekeeper.updateMonthlyBudget(profileEntity.monthlyBudget * 100L)
                    } else {
                        hasLoadedInitialProfile = true
                        _uiState.update { state ->
                            state.copy(
                                onboardingCompleted = sessionOnboardingCompleted,
                                isCheckingOnboarding = false,
                                isSyncingFirestore = false
                            )
                        }
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
                                        targetKcal = matched.targetKcal,
                                        options = if (slot.options.any { it.title == matched.selectedTitle }) {
                                            slot.options
                                        } else {
                                            listOf(MealOption(matched.selectedPlace, matched.selectedTitle, "৳${matched.selectedPrice}", matched.selectedPrice, matched.isPartner, matched.discountTag)) + slot.options
                                        }
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

            // Listen to Partner Restaurants
            launch {
                repo.observePartnerRestaurants().collect { partners ->
                    if (partners.isNotEmpty()) {
                        val filtered = partners.filter { it.areaId == _uiState.value.currentCampusArea.id }
                        _uiState.update { it.copy(nearbyPartnerRestaurants = if (filtered.isNotEmpty()) filtered else partners) }
                    }
                }
            }

            // Listen to Central Foods
            launch {
                repo.observeCentralFoods().collect { foods ->
                    if (foods.isNotEmpty()) {
                        val filtered = foods.filter { it.areaId == _uiState.value.currentCampusArea.id }
                        val activeList = if (filtered.isNotEmpty()) filtered else foods
                        _uiState.update { it.copy(nearbyCentralFoods = activeList) }
                        aiToolsGatekeeper.updateCatalogFromEntities(activeList)
                    }
                }
            }

            // Listen to Nearby Restaurants collection based on latitude/longitude
            launch {
                repo.observeNearbyRestaurants(_uiState.value.currentLatitude, _uiState.value.currentLongitude).collect { nearbyList ->
                    if (nearbyList.isNotEmpty()) {
                        _uiState.update { it.copy(nearbyRestaurantsWithDistance = nearbyList) }
                    }
                }
            }
        }
    }

    private fun initializeMeals() {
        val defaultArea = CampusLocationManager.getDefaultArea()
        val initialMeals = buildSlotsForArea(defaultArea)
        val currentHour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        val defaultExpanded = initialMeals.indexOfFirst { currentHour < it.activeHourLimit }.coerceAtLeast(0)

        _uiState.update {
            it.copy(
                currentCampusArea = defaultArea,
                nearbyPartnerRestaurants = defaultArea.defaultRestaurants,
                nearbyCentralFoods = defaultArea.defaultMeals,
                mealSlots = initialMeals,
                expandedMealIndex = defaultExpanded
            )
        }
        aiToolsGatekeeper.updateCatalogFromEntities(defaultArea.defaultMeals)
    }

    private fun buildSlotsForArea(area: CampusArea): List<MealSlot> {
        val slots = listOf(
            Triple("breakfast", "Breakfast", "🍳") to (35 to 11),
            Triple("lunch", "Lunch", "🍛") to (81 to 16),
            Triple("snacks", "Snacks", "🥪") to (30 to 19),
            Triple("dinner", "Dinner", "🍲") to (51 to 24)
        )

        return slots.map { (slotInfo, budgetAndHour) ->
            val (id, name, emoji) = slotInfo
            val (defaultBudget, hourLimit) = budgetAndHour
            val areaMeals = area.defaultMeals.filter { it.slot.equals(id, ignoreCase = true) }

            val options = areaMeals.map { meal ->
                MealOption(
                    place = meal.place,
                    title = meal.name,
                    priceStr = "৳${meal.priceTk}",
                    priceNum = meal.priceTk,
                    isPartner = meal.isPartner,
                    discountTag = meal.discountTag
                )
            }.ifEmpty {
                listOf(MealOption("Campus Canteen", "$name combo", "৳$defaultBudget", defaultBudget))
            }

            val selectedOpt = options.first()
            MealSlot(
                id = id,
                name = name,
                emoji = emoji,
                budget = selectedOpt.priceNum,
                targetKcal = areaMeals.firstOrNull()?.kcal ?: 500,
                activeHourLimit = hourLimit,
                options = options,
                selectedOptionIndex = 0
            )
        }
    }

    fun openCampusSelector() {
        _uiState.update { it.copy(showCampusSelectorDialog = true) }
    }

    fun dismissCampusSelector() {
        _uiState.update { it.copy(showCampusSelectorDialog = false) }
    }

    fun selectCampusArea(area: CampusArea) {
        val updatedSlots = buildSlotsForArea(area)
        _uiState.update {
            it.copy(
                currentCampusArea = area,
                currentLatitude = area.latitude,
                currentLongitude = area.longitude,
                nearbyPartnerRestaurants = area.defaultRestaurants,
                nearbyCentralFoods = area.defaultMeals,
                mealSlots = updatedSlots,
                showCampusSelectorDialog = false,
                undoNoticeMessage = "Switched to ${area.shortName}. Live nearby restaurants & meal catalog updated!"
            )
        }
        aiToolsGatekeeper.updateCatalogFromEntities(area.defaultMeals)

        currentUserId?.let { uid ->
            repository?.let { repo ->
                viewModelScope.launch {
                    repo.seedAreaRestaurantsAndFoods(area)
                    repo.syncUserMealsWithArea(uid, area)
                    val nearby = repo.getNearbyRestaurants(area.latitude, area.longitude)
                    if (nearby.isNotEmpty()) {
                        _uiState.update { it.copy(nearbyRestaurantsWithDistance = nearby) }
                    }
                }
            }
        }
    }

    fun detectLocationAndSetCampus(context: Context) {
        _uiState.update { it.copy(isLocatingGps = true) }
        viewModelScope.launch {
            try {
                val detected = CampusLocationManager.detectCampusFromDevice(context)
                selectCampusArea(detected)
            } finally {
                _uiState.update { it.copy(isLocatingGps = false) }
            }
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

    fun previousOnboardingStep(onExitToSignIn: () -> Unit = {}) {
        if (_uiState.value.onboardingStep > 1) {
            _uiState.update { it.copy(onboardingStep = it.onboardingStep - 1) }
        } else {
            onExitToSignIn()
        }
    }

    fun sendFirebasePhoneVerification(activity: Activity, rawPhoneNumber: String) {
        val clean = rawPhoneNumber.filter { it.isDigit() || it == '+' }
        val formatted = when {
            clean.startsWith("+") -> clean
            clean.startsWith("0") -> "+880" + clean.removePrefix("0")
            clean.isNotBlank() -> "+880$clean"
            else -> "+8801712345678"
        }

        _uiState.update {
            it.copy(
                isSendingOtp = true,
                phoneAuthStatus = "Sending SMS verification via Firebase to $formatted...",
                userGoals = it.userGoals.copy(phone = formatted)
            )
        }

        try {
            val callbacks = object : PhoneAuthProvider.OnVerificationStateChangedCallbacks() {
                override fun onVerificationCompleted(credential: PhoneAuthCredential) {
                    val code = credential.smsCode ?: _uiState.value.userGoals.otp
                    _uiState.update { state ->
                        state.copy(
                            isSendingOtp = false,
                            phoneAuthStatus = "Phone automatically verified via Firebase!",
                            userGoals = state.userGoals.copy(
                                phone = formatted,
                                otp = code,
                                isPhoneVerified = true
                            )
                        )
                    }
                }

                override fun onVerificationFailed(e: FirebaseException) {
                    Log.w(TAG, "Firebase phone verification unavailable: ${e.message}", e)
                    _uiState.update { state ->
                        state.copy(
                            isSendingOtp = false,
                            phoneAuthStatus = "Firebase SMS isn't enabled on this emulator/project yet. You can still enter your number & continue."
                        )
                    }
                }

                override fun onCodeSent(
                    verificationId: String,
                    token: PhoneAuthProvider.ForceResendingToken
                ) {
                    _uiState.update { state ->
                        state.copy(
                            isSendingOtp = false,
                            phoneVerificationId = verificationId,
                            phoneAuthStatus = "6-digit SMS code sent to $formatted.",
                            userGoals = state.userGoals.copy(phone = formatted)
                        )
                    }
                }
            }

            val options = PhoneAuthOptions.newBuilder(Firebase.auth)
                .setPhoneNumber(formatted)
                .setTimeout(60L, TimeUnit.SECONDS)
                .setActivity(activity)
                .setCallbacks(callbacks)
                .build()

            PhoneAuthProvider.verifyPhoneNumber(options)
        } catch (e: Exception) {
            Log.e(TAG, "Error initiating Firebase Phone Auth", e)
            _uiState.update { state ->
                state.copy(
                    isSendingOtp = false,
                    phoneAuthStatus = "Phone number saved. Tap Verify & continue to proceed."
                )
            }
        }
    }

    fun verifyOtpAndContinue(phoneNumber: String, enteredOtp: String) {
        val verificationId = _uiState.value.phoneVerificationId
        val currentUser = Firebase.auth.currentUser
        val cleanPhone = phoneNumber.trim().ifBlank { _uiState.value.userGoals.phone }
        val cleanOtp = enteredOtp.trim()

        if (verificationId != null && cleanOtp.length == 6 && currentUser != null) {
            try {
                val credential = PhoneAuthProvider.getCredential(verificationId, cleanOtp)
                currentUser.linkWithCredential(credential).addOnCompleteListener { task ->
                    if (task.isSuccessful) {
                        Log.d(TAG, "Phone credential linked to Firebase user")
                    } else {
                        Log.w(TAG, "Phone link fallback: ${task.exception?.message}")
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Phone credential check fallback: ${e.message}")
            }
        }
        _uiState.update { state ->
            state.copy(
                onboardingStep = 2,
                userGoals = state.userGoals.copy(
                    phone = cleanPhone,
                    otp = cleanOtp,
                    isPhoneVerified = verificationId != null || cleanPhone.isNotBlank()
                )
            )
        }
    }

    /**
     * Updates user goals in memory only (zero Firestore network calls, zero typing lag).
     */
    fun updateGoalsDraft(transform: (UserGoals) -> UserGoals) {
        _uiState.update {
            val updated = transform(it.userGoals)
            it.copy(userGoals = updated)
        }
        gatekeeper.updateMonthlyBudget(_uiState.value.userGoals.monthlyBudget * 100L)
    }

    fun completeOnboarding() {
        sessionOnboardingCompleted = true
        _uiState.update { state ->
            val g = state.userGoals
            val sanitized = g.copy(
                name = g.name.trim().ifBlank { "Student" }.take(100),
                phone = g.phone.trim().take(30),
                city = g.city.trim().ifBlank { "Dhaka" }.take(100),
                age = g.age.coerceIn(10, 120),
                heightCm = g.heightCm.coerceIn(50, 300),
                weightKg = g.weightKg.coerceIn(20, 400),
                university = g.university.trim().take(100),
                monthlyBudget = g.monthlyBudget.coerceAtLeast(0),
                dailyCalorieGoal = g.calculateMifflinStJeor().coerceIn(500, 10000)
            )
            state.copy(
                userGoals = sanitized,
                onboardingCompleted = true,
                isCheckingOnboarding = false,
                currentTab = AppNavTab.HOME
            )
        }
        gatekeeper.updateMonthlyBudget(_uiState.value.userGoals.monthlyBudget * 100L)
        syncGoalsToFirestore()
    }

    fun restartOnboarding() {
        sessionOnboardingCompleted = false
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
        val g = _uiState.value.userGoals
        val isOnboardingDone = _uiState.value.onboardingCompleted || sessionOnboardingCompleted
        viewModelScope.launch {
            try {
                repo.saveUserProfile(
                    UserProfileEntity(
                        userId = uid,
                        name = g.name.trim().ifBlank { "Student" }.take(100),
                        phone = g.phone.trim().take(30),
                        city = g.city.trim().ifBlank { "Dhaka" }.take(100),
                        age = g.age.coerceIn(10, 120),
                        heightCm = g.heightCm.coerceIn(50, 300),
                        weightKg = g.weightKg.coerceIn(20, 400),
                        isVegetarian = g.isVegetarian,
                        allowLocation = g.allowLocation,
                        goesToGym = g.goesToGym,
                        isStudent = g.isStudent,
                        university = g.university.trim().take(100),
                        monthlyBudget = g.monthlyBudget.coerceAtLeast(0),
                        totalSpentThisMonth = _uiState.value.totalSpentThisMonth.coerceAtLeast(0),
                        totalSavedFromDiscounts = _uiState.value.totalSavedFromDiscounts.coerceAtLeast(0),
                        unallocatedPaisa = feastState.value.unallocatedPaisa.coerceAtLeast(0L),
                        onMessPlan = g.onMessPlan,
                        dailyCalorieGoal = g.dailyCalorieGoal.coerceIn(500, 10000),
                        onboardingCompleted = isOnboardingDone
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
        _uiState.update { it.copy(chatMessages = it.chatMessages + userMsg, isAiThinking = true) }

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
                        _uiState.update { state ->
                            val existingIndex = state.chatMessages.indexOfFirst { it.id == stepMsg.id }
                            val updatedList = if (existingIndex >= 0) {
                                state.chatMessages.toMutableList().apply { set(existingIndex, stepMsg) }
                            } else {
                                state.chatMessages + stepMsg
                            }
                            state.copy(chatMessages = updatedList)
                        }
                    }
                )

                if (finalMsg.isPendingConfirmation && finalMsg.tradeoffToken != null) {
                    val stored = chatEngine.toolsGatekeeper.getStoredToken(finalMsg.tradeoffToken)
                    val candidateFeast = stored?.feastToCancel ?: feastState.value.feasts.firstOrNull { !it.isCancelled }
                    val slotId = stored?.slotId ?: "lunch"
                    val slot = _uiState.value.mealSlots.find { it.id == slotId }
                    val slotAllowance = feastState.value.mealAllowancesPaisa[slotId] ?: 8_088L
                    val proposal = TradeoffProposal(
                        mealId = slotId,
                        mealName = slot?.name ?: slotId.replaceFirstChar { it.uppercase() },
                        currentAllowancePaisa = slotAllowance,
                        requestedItemName = stored?.item?.name ?: "Kacchi Biriyani",
                        requestedPlaceName = stored?.item?.place ?: "Star Kabab & Biriyani",
                        requestedPricePaisa = stored?.pricePaisa ?: 12_000L,
                        requestedKcal = stored?.item?.kcal ?: 850,
                        shortfallPaisa = stored?.shortfallPaisa ?: 3_912L,
                        usedUnallocatedPaisa = stored?.unallocatedUsedPaisa ?: 0L,
                        feastToCancel = candidateFeast,
                        remainderToUnallocatedPaisa = if (candidateFeast != null) {
                            (candidateFeast.budgetPaisa - (stored?.shortfallPaisa ?: 3_912L)).coerceAtLeast(0L)
                        } else 0L,
                        plainEnglishExplanation = finalMsg.content
                    )
                    _uiState.update { it.copy(activeTradeoffProposal = proposal) }
                }
            } catch (e: TimeoutCancellationException) {
                Log.e(TAG, "AI generation timed out, using smart copilot fallback", e)
                val intelligentContent = chatEngine.generateSmartCopilotResponse(
                    userText = userText,
                    currentSlots = _uiState.value.mealSlots,
                    isVegetarian = _uiState.value.userGoals.isVegetarian
                )
                val fallbackMsg = ChatMessage(
                    id = UUID.randomUUID().toString(),
                    sender = MessageSender.ASSISTANT,
                    content = intelligentContent,
                    isLocalExecution = true
                )
                _uiState.update { it.copy(chatMessages = it.chatMessages + fallbackMsg) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Chat engine exception, using smart copilot fallback", e)
                val intelligentContent = chatEngine.generateSmartCopilotResponse(
                    userText = userText,
                    currentSlots = _uiState.value.mealSlots,
                    isVegetarian = _uiState.value.userGoals.isVegetarian
                )
                val fallbackMsg = ChatMessage(
                    id = UUID.randomUUID().toString(),
                    sender = MessageSender.ASSISTANT,
                    content = intelligentContent,
                    isLocalExecution = true
                )
                _uiState.update { it.copy(chatMessages = it.chatMessages + fallbackMsg) }
            } finally {
                _uiState.update { it.copy(isAiThinking = false) }
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
                    toolResult = "Feast cancelled, remainder returned to U, ${proposal.mealId} allowance updated",
                    latencyMs = 8L,
                    isLocalExecution = true
                )
                val confirmReply = ChatMessage(
                    id = UUID.randomUUID().toString(),
                    sender = MessageSender.ASSISTANT,
                    content = "Trade-off confirmed! ${proposal.requestedItemName} is scheduled for ${proposal.mealName}. Friday's feast has been cancelled and the remaining savings returned to your buffer.",
                    latencyMs = 12L,
                    isLocalExecution = true
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
