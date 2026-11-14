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
import com.example.nutrimate.data.model.ChatMessageEntity
import com.example.nutrimate.data.model.MealItemEntity
import com.example.nutrimate.data.model.PartnerRestaurantEntity
import com.example.nutrimate.data.model.PaymentRecordEntity
import com.example.nutrimate.data.model.UserProfileEntity
import com.example.nutrimate.data.repository.NutriMateFirestoreRepository
import com.example.nutrimate.model.*
import com.google.firebase.Firebase
import com.google.firebase.FirebaseException
import com.google.firebase.FirebaseTooManyRequestsException
import com.google.firebase.auth.FirebaseAuthException
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.PhoneAuthCredential
import com.google.firebase.auth.PhoneAuthOptions
import com.google.firebase.auth.PhoneAuthProvider
import com.google.firebase.auth.auth
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
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
    val isStreamingResponse: Boolean = false,
    val isSendingOtp: Boolean = false,
    val isVerifyingOtp: Boolean = false,
    val phoneAuthStatus: String? = null,
    val phoneAuthError: String? = null,
    val phoneVerificationId: String? = null,
    val resendCooldownSeconds: Int = 0
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
    private var activeChatJob: Job? = null
    private var forceResendingToken: PhoneAuthProvider.ForceResendingToken? = null
    private var resendCooldownJob: Job? = null

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

            // Hydrate recent persisted multi-turn chat history (bounded) without blocking onboarding
            launch {
                val persisted = repo.loadRecentChatMessages(userId, limit = 20L)
                if (persisted.isNotEmpty()) {
                    val mapped = persisted.map { entity ->
                        ChatMessage(
                            id = entity.id,
                            sender = if (entity.sender == MessageSender.USER.name) MessageSender.USER else MessageSender.ASSISTANT,
                            content = entity.content,
                            timestamp = entity.timestamp,
                            latencyMs = entity.latencyMs,
                            firstTokenMs = entity.firstTokenMs,
                            isLocalExecution = entity.isLocalExecution
                        )
                    }
                    _uiState.update { it.copy(chatMessages = mapped) }
                    chatEngine.hydrateHistory(mapped)
                }
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
                                            val coords = com.example.nutrimate.data.location.MapDirectionsHelper.resolveCoordinates(
                                                placeName = matched.selectedPlace,
                                                storedLat = matched.latitude,
                                                storedLon = matched.longitude,
                                                fallbackArea = state.currentCampusArea
                                            )
                                            listOf(
                                                MealOption(
                                                    place = matched.selectedPlace,
                                                    title = matched.selectedTitle,
                                                    priceStr = "৳${matched.selectedPrice}",
                                                    priceNum = matched.selectedPrice,
                                                    isPartner = matched.isPartner,
                                                    discountTag = matched.discountTag,
                                                    latitude = coords.latitude,
                                                    longitude = coords.longitude
                                                )
                                            ) + slot.options
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
                val coords = com.example.nutrimate.data.location.MapDirectionsHelper.resolveCoordinates(
                    placeName = meal.place,
                    storedLat = meal.latitude,
                    storedLon = meal.longitude,
                    fallbackArea = area
                )
                MealOption(
                    place = meal.place,
                    title = meal.name,
                    priceStr = "৳${meal.priceTk}",
                    priceNum = meal.priceTk,
                    isPartner = meal.isPartner,
                    discountTag = meal.discountTag,
                    latitude = coords.latitude,
                    longitude = coords.longitude
                )
            }.ifEmpty {
                val coords = com.example.nutrimate.data.location.MapDirectionsHelper.resolveCoordinates(
                    placeName = "Campus Canteen",
                    fallbackArea = area
                )
                listOf(
                    MealOption(
                        place = "Campus Canteen",
                        title = "$name combo",
                        priceStr = "৳$defaultBudget",
                        priceNum = defaultBudget,
                        latitude = coords.latitude,
                        longitude = coords.longitude
                    )
                )
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

    fun formatPhoneE164(rawPhoneNumber: String): String {
        val stripped = rawPhoneNumber.trim().replace(Regex("[\\s\\-()]"), "")
        return when {
            stripped.startsWith("+") -> "+" + stripped.drop(1).filter { it.isDigit() }
            stripped.startsWith("00") -> "+" + stripped.drop(2).filter { it.isDigit() }
            stripped.startsWith("880") -> "+" + stripped.filter { it.isDigit() }
            stripped.startsWith("0") -> "+880" + stripped.drop(1).filter { it.isDigit() }
            stripped.startsWith("1") && stripped.length == 10 -> "+880$stripped"
            stripped.isNotBlank() -> "+880" + stripped.filter { it.isDigit() }
            else -> ""
        }
    }

    private fun startResendCooldown(seconds: Int = 60) {
        resendCooldownJob?.cancel()
        resendCooldownJob = viewModelScope.launch {
            for (remaining in seconds downTo 1) {
                _uiState.update { it.copy(resendCooldownSeconds = remaining) }
                delay(1_000L)
            }
            _uiState.update { it.copy(resendCooldownSeconds = 0) }
        }
    }

    fun clearPhoneAuthMessages() {
        _uiState.update { it.copy(phoneAuthError = null, phoneAuthStatus = null) }
    }

    fun sendFirebasePhoneVerification(
        activity: Activity,
        rawPhoneNumber: String,
        isResend: Boolean = false
    ) {
        val formatted = formatPhoneE164(rawPhoneNumber)
        val digitsCount = formatted.drop(1).count { it.isDigit() }
        if (formatted.isBlank() || digitsCount < 8 || digitsCount > 15) {
            _uiState.update {
                it.copy(
                    isSendingOtp = false,
                    phoneAuthStatus = null,
                    phoneAuthError = "Enter a valid mobile number (e.g. 01712345678 or +8801712345678)."
                )
            }
            return
        }

        _uiState.update {
            it.copy(
                isSendingOtp = true,
                phoneAuthError = null,
                phoneAuthStatus = if (isResend) "Resending OTP to $formatted…" else "Sending 6-digit SMS code to $formatted…",
                userGoals = it.userGoals.copy(phone = formatted, isPhoneVerified = false)
            )
        }

        try {
            val callbacks = object : PhoneAuthProvider.OnVerificationStateChangedCallbacks() {
                override fun onVerificationCompleted(credential: PhoneAuthCredential) {
                    val autoCode = credential.smsCode ?: _uiState.value.userGoals.otp
                    viewModelScope.launch {
                        linkOrUpdatePhoneCredential(
                            credential = credential,
                            formattedPhone = formatted,
                            autoCode = autoCode,
                            autoAdvance = false
                        )
                    }
                }

                override fun onVerificationFailed(e: FirebaseException) {
                    Log.w(TAG, "Firebase phone verification failed: ${e.message}", e)
                    val readableError = when (e) {
                        is FirebaseAuthInvalidCredentialsException ->
                            "Invalid phone number format or request. Check your number ($formatted)."
                        is FirebaseTooManyRequestsException ->
                            "SMS quota temporarily reached. Wait a few minutes and try again."
                        else ->
                            e.localizedMessage ?: "Couldn't send verification SMS. Please check your connection."
                    }
                    _uiState.update { state ->
                        state.copy(
                            isSendingOtp = false,
                            phoneAuthStatus = null,
                            phoneAuthError = readableError
                        )
                    }
                }

                override fun onCodeSent(
                    verificationId: String,
                    token: PhoneAuthProvider.ForceResendingToken
                ) {
                    forceResendingToken = token
                    startResendCooldown(60)
                    _uiState.update { state ->
                        state.copy(
                            isSendingOtp = false,
                            phoneVerificationId = verificationId,
                            phoneAuthError = null,
                            phoneAuthStatus = "6-digit SMS code sent to $formatted",
                            userGoals = state.userGoals.copy(phone = formatted)
                        )
                    }
                }
            }

            val builder = PhoneAuthOptions.newBuilder(Firebase.auth)
                .setPhoneNumber(formatted)
                .setTimeout(60L, TimeUnit.SECONDS)
                .setActivity(activity)
                .setCallbacks(callbacks)

            if (isResend) {
                forceResendingToken?.let { builder.setForceResendingToken(it) }
            }

            PhoneAuthProvider.verifyPhoneNumber(builder.build())
        } catch (e: Exception) {
            Log.e(TAG, "Error initiating Firebase Phone Auth", e)
            _uiState.update { state ->
                state.copy(
                    isSendingOtp = false,
                    phoneAuthStatus = null,
                    phoneAuthError = e.localizedMessage ?: "Unable to start Firebase phone verification."
                )
            }
        }
    }

    private suspend fun linkOrUpdatePhoneCredential(
        credential: PhoneAuthCredential,
        formattedPhone: String,
        autoCode: String = "",
        autoAdvance: Boolean = true
    ) {
        _uiState.update { it.copy(isVerifyingOtp = true, phoneAuthError = null) }
        try {
            val currentUser = Firebase.auth.currentUser
            if (currentUser == null) {
                Firebase.auth.signInWithCredential(credential).await()
            } else {
                try {
                    currentUser.linkWithCredential(credential).await()
                    Log.d(TAG, "Phone credential linked to Firebase user")
                } catch (e: FirebaseAuthException) {
                    when (e.errorCode) {
                        "ERROR_PROVIDER_ALREADY_LINKED" -> {
                            currentUser.updatePhoneNumber(credential).await()
                            Log.d(TAG, "Updated Firebase user phone number")
                        }
                        else -> throw e
                    }
                }
            }

            val resolvedPhone = Firebase.auth.currentUser?.phoneNumber?.takeIf { it.isNotBlank() } ?: formattedPhone
            resendCooldownJob?.cancel()
            _uiState.update { state ->
                state.copy(
                    isSendingOtp = false,
                    isVerifyingOtp = false,
                    resendCooldownSeconds = 0,
                    phoneAuthError = null,
                    phoneAuthStatus = "Phone verified with Firebase!",
                    onboardingStep = if (autoAdvance) 2 else state.onboardingStep,
                    userGoals = state.userGoals.copy(
                        phone = resolvedPhone,
                        otp = autoCode.ifBlank { state.userGoals.otp },
                        isPhoneVerified = true
                    )
                )
            }
        } catch (e: FirebaseAuthException) {
            Log.w(TAG, "Firebase Phone OTP verification error [${e.errorCode}]: ${e.message}", e)
            val errMsg = when (e.errorCode) {
                "ERROR_INVALID_CODE" -> "Incorrect 6-digit OTP code. Please check your SMS and try again."
                "ERROR_SESSION_EXPIRED" -> "OTP code expired. Tap 'Resend code' to get a new SMS."
                "ERROR_CREDENTIAL_ALREADY_IN_USE" -> "This phone number is already linked to another NutriMate account."
                else -> e.localizedMessage ?: "Phone verification failed (${e.errorCode})."
            }
            _uiState.update {
                it.copy(
                    isSendingOtp = false,
                    isVerifyingOtp = false,
                    phoneAuthStatus = null,
                    phoneAuthError = errMsg
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Unexpected error verifying phone OTP", e)
            _uiState.update {
                it.copy(
                    isSendingOtp = false,
                    isVerifyingOtp = false,
                    phoneAuthStatus = null,
                    phoneAuthError = e.localizedMessage ?: "Verification failed. Please try again."
                )
            }
        }
    }

    fun verifyOtpAndContinue(phoneNumber: String, enteredOtp: String) {
        val state = _uiState.value
        val formattedPhone = formatPhoneE164(phoneNumber).ifBlank { state.userGoals.phone }
        val cleanOtp = enteredOtp.filter { it.isDigit() }

        if (state.userGoals.isPhoneVerified && formattedPhone.isNotBlank()) {
            _uiState.update {
                it.copy(
                    onboardingStep = 2,
                    phoneAuthError = null,
                    userGoals = it.userGoals.copy(phone = formattedPhone)
                )
            }
            return
        }

        val verificationId = state.phoneVerificationId
        if (verificationId.isNullOrBlank()) {
            _uiState.update {
                it.copy(
                    phoneAuthStatus = null,
                    phoneAuthError = "Tap 'Send OTP' first to receive a 6-digit SMS code on your phone."
                )
            }
            return
        }

        if (cleanOtp.length != 6) {
            _uiState.update {
                it.copy(
                    phoneAuthStatus = null,
                    phoneAuthError = "Enter all 6 digits of the SMS verification code."
                )
            }
            return
        }

        viewModelScope.launch {
            val credential = PhoneAuthProvider.getCredential(verificationId, cleanOtp)
            linkOrUpdatePhoneCredential(
                credential = credential,
                formattedPhone = formattedPhone,
                autoCode = cleanOtp,
                autoAdvance = true
            )
        }
    }

    fun skipPhoneVerification(rawPhoneNumber: String) {
        val formatted = formatPhoneE164(rawPhoneNumber)
        _uiState.update { state ->
            state.copy(
                onboardingStep = 2,
                phoneAuthError = null,
                userGoals = state.userGoals.copy(
                    phone = formatted,
                    isPhoneVerified = state.userGoals.isPhoneVerified
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
                    val coords = com.example.nutrimate.data.location.MapDirectionsHelper.resolveCoordinates(
                        placeName = placeName,
                        fallbackArea = state.currentCampusArea
                    )
                    val newOpt = MealOption(
                        place = placeName,
                        title = itemName,
                        priceStr = "৳$priceTaka",
                        priceNum = priceTaka,
                        latitude = coords.latitude,
                        longitude = coords.longitude
                    )
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

    private fun buildUserContext(dateString: String): NutriMateUserContext {
        val state = _uiState.value
        val g = state.userGoals
        val fState = feastState.value
        val plannedKcal = state.mealSlots.sumOf { it.targetKcal }
        val remainingKcal = (g.dailyCalorieGoal - plannedKcal).coerceAtLeast(0)
        val remainingMonthlyTk = (g.monthlyBudget - state.totalSpentThisMonth).coerceAtLeast(0)

        val heightM = (g.heightCm / 100.0).coerceAtLeast(0.5)
        val bmi = g.weightKg / (heightM * heightM)
        val inferredGoal = when {
            bmi < 18.5 -> "Healthy weight gain & calorie surplus"
            bmi > 25.0 -> "Sustainable fat loss & balanced nutrition"
            g.goesToGym -> "Muscle building & fitness recovery"
            else -> "Balanced weight maintenance"
        }

        val prefs = buildList {
            add("Bangladeshi campus food")
            add(if (g.isVegetarian) "Vegetarian" else "Non-vegetarian")
            if (g.goesToGym) add("High-protein gym support")
            if (g.onMessPlan) add("Student hall mess plan")
        }

        val slotAllowances = fState.mealAllowancesPaisa.mapValues { (_, paisa) -> (paisa / 100L).toInt() }
            .ifEmpty { state.mealSlots.associate { it.id to it.budget } }

        val todayTotalAllowanceTk = slotAllowances.values.sum().takeIf { it > 0 } ?: state.mealSlots.sumOf { it.budget }
        val plannedMeals = state.mealSlots.map { slot ->
            val opt = slot.activeOption
            "${slot.name}: ${opt.title} at ${opt.place} (৳${opt.priceNum}, ${slot.targetKcal} kcal)"
        }
        val nextFeast = fState.feasts.firstOrNull { !it.isCancelled }?.let {
            "${it.title} (৳${it.budgetPaisa / 100L})"
        }
        val partnerDeals = if (state.nearbyRestaurantsWithDistance.isNotEmpty()) {
            state.nearbyRestaurantsWithDistance
                .filter { it.restaurant.isPartner && it.distanceKm <= 10.0 }
                .take(4)
                .map { "${it.restaurant.name} (${it.restaurant.discountTag}, ${it.distanceFormatted})" }
        } else {
            state.nearbyPartnerRestaurants.take(4).map { "${it.name} (${it.discountTag})" }
        }

        return NutriMateUserContext(
            userName = g.name.ifBlank { "Student" },
            age = g.age,
            userGoal = inferredGoal,
            dailyCalorieTarget = g.dailyCalorieGoal,
            plannedCaloriesToday = plannedKcal,
            remainingCaloriesToday = remainingKcal,
            monthlyBudgetTk = g.monthlyBudget,
            monthlySpentTk = state.totalSpentThisMonth,
            remainingMonthlyBudgetTk = remainingMonthlyTk,
            todaySlotAllowanceTotalTk = todayTotalAllowanceTk,
            unallocatedReserveTk = (fState.unallocatedPaisa / 100L).toInt(),
            dietaryPreferences = prefs,
            isStudent = g.isStudent,
            campusArea = state.currentCampusArea.shortName,
            university = g.university.ifBlank { state.currentCampusArea.universities.firstOrNull() ?: "Campus" },
            onMessPlan = g.onMessPlan,
            plannedMealsToday = plannedMeals,
            slotAllowancesTk = slotAllowances,
            activeFeast = nextFeast,
            nearbyPartnerDeals = partnerDeals,
            dateString = dateString
        )
    }

    fun cancelChatGeneration() {
        val hadJob = activeChatJob?.isActive == true
        activeChatJob?.cancel()
        activeChatJob = null
        if (hadJob) {
            _uiState.update { state ->
                val finalized = state.chatMessages.map { msg ->
                    if (msg.isStreaming) msg.copy(isStreaming = false) else msg
                }
                state.copy(
                    chatMessages = finalized,
                    isAiThinking = false,
                    isStreamingResponse = false
                )
            }
        }
    }

    fun clearChatHistory() {
        cancelChatGeneration()
        chatEngine.resetChatSession()
        _uiState.update {
            it.copy(
                chatMessages = emptyList(),
                isAiThinking = false,
                isStreamingResponse = false,
                activeTradeoffProposal = null
            )
        }
        currentUserId?.let { uid ->
            repository?.let { repo ->
                viewModelScope.launch {
                    repo.clearChatHistory(uid)
                }
            }
        }
    }

    fun retryChatMessage(errorMessageId: String) {
        val target = _uiState.value.chatMessages.find { it.id == errorMessageId } ?: return
        val prompt = target.retryPrompt?.takeIf { it.isNotBlank() } ?: return
        _uiState.update { state ->
            state.copy(chatMessages = state.chatMessages.filterNot { it.id == errorMessageId })
        }
        sendChatMessage(userText = prompt, appendUserMessage = false)
    }

    fun sendChatMessage(userText: String, appendUserMessage: Boolean = true) {
        val cleanText = userText.trim()
        if (cleanText.isBlank()) return
        // Prevent duplicate concurrent requests while waiting for TTFT or streaming
        if (activeChatJob?.isActive == true || _uiState.value.isAiThinking || _uiState.value.isStreamingResponse) {
            Log.w(TAG, "Ignoring duplicate sendChatMessage while request is already in flight")
            return
        }

        val userMsg = ChatMessage(
            id = "msg_${System.currentTimeMillis()}_${UUID.randomUUID().toString().take(6)}",
            sender = MessageSender.USER,
            content = cleanText
        )
        _uiState.update { state ->
            state.copy(
                chatMessages = if (appendUserMessage) state.chatMessages + userMsg else state.chatMessages,
                isAiThinking = true,
                isStreamingResponse = false
            )
        }

        if (appendUserMessage) {
            currentUserId?.let { uid ->
                repository?.let { repo ->
                    viewModelScope.launch {
                        repo.saveCompletedChatMessage(
                            uid,
                            ChatMessageEntity(
                                id = userMsg.id,
                                sender = MessageSender.USER.name,
                                content = userMsg.content,
                                timestamp = userMsg.timestamp,
                                isLocalExecution = false
                            )
                        )
                    }
                }
            }
        }

        activeChatJob = viewModelScope.launch {
            try {
                val tContextStart = System.currentTimeMillis()
                val dateStr = SimpleDateFormat("EEEE, MMMM d", Locale.getDefault()).format(Calendar.getInstance().time)
                val userContext = buildUserContext(dateStr)
                val contextBuildMs = (System.currentTimeMillis() - tContextStart).coerceAtLeast(1L)

                val finalMsg = chatEngine.processUserMessage(
                    userText = cleanText,
                    currentSlots = _uiState.value.mealSlots,
                    userContext = userContext,
                    nearbyRestaurants = _uiState.value.nearbyRestaurantsWithDistance,
                    campusArea = _uiState.value.currentCampusArea,
                    contextBuildMs = contextBuildMs,
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
                            val streamingNow = stepMsg.sender == MessageSender.ASSISTANT && stepMsg.isStreaming
                            state.copy(
                                chatMessages = updatedList,
                                isAiThinking = if (streamingNow || (stepMsg.sender == MessageSender.ASSISTANT && !stepMsg.isStreaming)) false else state.isAiThinking,
                                isStreamingResponse = streamingNow
                            )
                        }
                    }
                )

                // Persist final completed assistant response once (never per streaming chunk)
                if (!finalMsg.isError && !finalMsg.isStreaming && finalMsg.content.isNotBlank() && finalMsg.sender == MessageSender.ASSISTANT) {
                    currentUserId?.let { uid ->
                        repository?.saveCompletedChatMessage(
                            uid,
                            ChatMessageEntity(
                                id = finalMsg.id,
                                sender = MessageSender.ASSISTANT.name,
                                content = finalMsg.content,
                                timestamp = finalMsg.timestamp,
                                latencyMs = finalMsg.latencyMs,
                                firstTokenMs = finalMsg.firstTokenMs,
                                isLocalExecution = finalMsg.isLocalExecution
                            )
                        )
                    }
                }
            } catch (e: CancellationException) {
                _uiState.update { state ->
                    state.copy(
                        chatMessages = state.chatMessages.map { if (it.isStreaming) it.copy(isStreaming = false) else it },
                        isAiThinking = false,
                        isStreamingResponse = false
                    )
                }
                throw e
            } catch (e: Exception) {
                Log.e("NUTRIMATE_AI", "Unexpected ViewModel chat pipeline error", e)
                val errMsg = ChatMessage(
                    id = "err_${System.currentTimeMillis()}",
                    sender = MessageSender.ASSISTANT,
                    content = "Couldn't complete the AI response (${e.localizedMessage ?: "unexpected error"}).",
                    isLocalExecution = true,
                    isError = true,
                    retryPrompt = cleanText
                )
                _uiState.update { it.copy(chatMessages = it.chatMessages + errMsg) }
            } finally {
                activeChatJob = null
                _uiState.update { it.copy(isAiThinking = false, isStreamingResponse = false) }
            }
        }
    }

    fun confirmInChatTradeoff(token: String) {
        viewModelScope.launch {
            val storedBefore = chatEngine.toolsGatekeeper.getStoredToken(token)
            val proposal = chatEngine.toolsGatekeeper.confirmTradeoff(
                token = token,
                currentSlots = _uiState.value.mealSlots,
                userId = currentUserId,
                repository = repository
            )
            if (proposal != null) {
                val priceTk = (proposal.requestedPricePaisa / 100).toInt()
                updateSlotOptionDirectly(
                    slotId = proposal.mealId,
                    itemName = proposal.requestedItemName,
                    placeName = proposal.requestedPlaceName,
                    priceTaka = priceTk,
                    kcal = proposal.requestedKcal
                )
                val badgeResult = when {
                    storedBefore?.isManualLog == true -> "Logged ${proposal.requestedItemName} (৳$priceTk) to ${proposal.mealId}"
                    proposal.feastToCancel != null -> "Feast cancelled, remainder returned to reserve, ${proposal.mealId} updated"
                    proposal.usedUnallocatedPaisa > 0 -> "Funded from unallocated reserve, ${proposal.mealId} updated"
                    else -> "Applied within allowance, ${proposal.mealId} updated"
                }
                val confirmBadge = ChatMessage(
                    id = UUID.randomUUID().toString(),
                    sender = MessageSender.FUNCTION_CALL,
                    content = "gatekeeper.confirm(token=\"${token.take(8)}...\")",
                    toolName = "gatekeeper.confirm",
                    toolArgs = "token=\"${token.take(8)}...\"",
                    toolResult = badgeResult,
                    toolSource = "local",
                    latencyMs = 6L,
                    isLocalExecution = true
                )
                val replyText = when {
                    storedBefore?.isManualLog == true ->
                        "Meal logged! ${proposal.requestedItemName} from ${proposal.requestedPlaceName} (৳$priceTk, ${proposal.requestedKcal} kcal) is saved to ${proposal.mealName}."
                    proposal.feastToCancel != null ->
                        "Trade-off confirmed! ${proposal.requestedItemName} is scheduled for ${proposal.mealName}. ${proposal.feastToCancel.title} has been cancelled and the remaining savings returned to your buffer."
                    proposal.usedUnallocatedPaisa > 0 ->
                        "Swap confirmed! ${proposal.requestedItemName} (৳$priceTk) is scheduled for ${proposal.mealName} using ৳${(proposal.usedUnallocatedPaisa / 100.0).toInt()} from your unallocated reserve."
                    else ->
                        "Swap confirmed! ${proposal.requestedItemName} (৳$priceTk, ${proposal.requestedKcal} kcal) is now scheduled for ${proposal.mealName}."
                }
                val confirmReply = ChatMessage(
                    id = UUID.randomUUID().toString(),
                    sender = MessageSender.ASSISTANT,
                    content = replyText,
                    latencyMs = 8L,
                    isLocalExecution = true
                )
                _uiState.update { state ->
                    val updatedMessages = state.chatMessages.map {
                        if (it.tradeoffToken == token) it.copy(isPendingConfirmation = false) else it
                    } + confirmBadge + confirmReply
                    state.copy(
                        chatMessages = updatedMessages,
                        activeTradeoffProposal = null,
                        undoNoticeMessage = "Updated ${proposal.mealName}: ${proposal.requestedItemName}."
                    )
                }
            }
        }
    }

    fun declineInChatTradeoff(token: String) {
        val stored = chatEngine.toolsGatekeeper.getStoredToken(token)
        val slotId = stored?.slotId ?: "lunch"
        val alternatives = chatEngine.toolsGatekeeper.getAlternatives(slotId)
        val altText = alternatives.joinToString { "${it.name} (৳${it.priceTk})" }
        val cancelReply = ChatMessage(
            id = UUID.randomUUID().toString(),
            sender = MessageSender.ASSISTANT,
            content = if (altText.isNotBlank()) {
                "Cancelled — your meal plan and feasts remain unchanged! Other $slotId options within allowance: $altText."
            } else {
                "Cancelled — your meal plan and feasts remain unchanged!"
            }
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
