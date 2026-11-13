package com.example.nutrimate.ui.screens

import android.app.Activity
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Sms
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.nutrimate.R
import com.example.nutrimate.ui.components.GlassBox
import com.example.nutrimate.ui.components.GlassButton
import com.example.nutrimate.ui.components.GlassToggleRow
import com.example.nutrimate.ui.theme.*
import com.example.nutrimate.viewmodel.NutriMateViewModel
import com.google.firebase.Firebase
import com.google.firebase.auth.auth

@Composable
fun OnboardingScreen(
    viewModel: NutriMateViewModel,
    onSignOut: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val activity = context as? Activity
    val focusManager = LocalFocusManager.current
    val uiState by viewModel.uiState.collectAsState()
    val goals = uiState.userGoals
    val step = uiState.onboardingStep
    val scrollState = rememberScrollState()

    val googleDisplayName = remember {
        Firebase.auth.currentUser?.displayName
            ?.trim()
            ?.takeIf { it.isNotBlank() && it != "Student" && it != "Dhruv" }
            .orEmpty()
    }

    // Start onboarding fields cleanly blank (or with real user/Google profile info, never fake placeholders)
    var phoneText by rememberSaveable { mutableStateOf(goals.phone) }
    var otpText by rememberSaveable { mutableStateOf("") }
    var nameText by rememberSaveable {
        mutableStateOf(
            goals.name.takeIf { it.isNotBlank() && it != "Student" && it != "Dhruv" } ?: googleDisplayName
        )
    }
    var ageText by rememberSaveable { mutableStateOf("") }
    var cityText by rememberSaveable { mutableStateOf(goals.city) }
    var heightText by rememberSaveable { mutableStateOf("") }
    var weightText by rememberSaveable { mutableStateOf("") }
    var universityText by rememberSaveable { mutableStateOf(goals.university) }
    var budgetText by rememberSaveable { mutableStateOf("") }

    var isVegetarian by rememberSaveable { mutableStateOf(goals.isVegetarian) }
    var allowLocation by rememberSaveable { mutableStateOf(goals.allowLocation) }
    var goesToGym by rememberSaveable { mutableStateOf(goals.goesToGym) }
    var isStudent by rememberSaveable { mutableStateOf(goals.isStudent) }
    var onMessPlan by rememberSaveable { mutableStateOf(goals.onMessPlan) }

    var step2ValidationAttempted by rememberSaveable { mutableStateOf(false) }
    var step3ValidationAttempted by rememberSaveable { mutableStateOf(false) }

    // Sync auto-detected SMS OTP or verified phone number from Firebase
    LaunchedEffect(goals.isPhoneVerified, goals.otp, goals.phone) {
        if (goals.isPhoneVerified) {
            if (goals.otp.isNotBlank()) otpText = goals.otp
            if (goals.phone.isNotBlank()) phoneText = goals.phone
        }
    }
    LaunchedEffect(goals.name) {
        if (nameText.isBlank() && goals.name.isNotBlank() && goals.name != "Student" && goals.name != "Dhruv") {
            nameText = goals.name
        }
    }

    fun commitDraftToViewModel() {
        val parsedAge = ageText.toIntOrNull()?.coerceIn(10, 120) ?: goals.age
        val parsedHeight = heightText.toIntOrNull()?.coerceIn(50, 300) ?: goals.heightCm
        val parsedWeight = weightText.toIntOrNull()?.coerceIn(20, 400) ?: goals.weightKg
        val parsedBudget = budgetText.toIntOrNull()?.coerceAtLeast(0) ?: goals.monthlyBudget
        viewModel.updateGoalsDraft { current ->
            val updated = current.copy(
                phone = viewModel.formatPhoneE164(phoneText).ifBlank { phoneText.trim() },
                otp = otpText.trim(),
                name = nameText.trim().ifBlank { "Student" },
                age = parsedAge,
                city = cityText.trim().ifBlank { "Dhaka" },
                heightCm = parsedHeight,
                weightKg = parsedWeight,
                isVegetarian = isVegetarian,
                allowLocation = allowLocation,
                goesToGym = goesToGym,
                isStudent = isStudent,
                university = universityText.trim(),
                monthlyBudget = parsedBudget,
                onMessPlan = onMessPlan
            )
            updated.copy(dailyCalorieGoal = updated.calculateMifflinStJeor())
        }
    }

    val hasCompleteBodyMetrics = ageText.toIntOrNull() in 10..120 &&
        heightText.toIntOrNull() in 50..300 &&
        weightText.toIntOrNull() in 20..400

    val liveCalories = remember(ageText, heightText, weightText, goesToGym) {
        val a = ageText.toIntOrNull()?.coerceIn(10, 120) ?: 21
        val h = heightText.toIntOrNull()?.coerceIn(50, 300) ?: 172
        val w = weightText.toIntOrNull()?.coerceIn(20, 400) ?: 64
        val bmr = 10 * w + 6.25 * h - 5 * a + 5
        val mult = if (goesToGym) 1.55 else 1.375
        (bmr * mult).toInt().coerceIn(500, 10000)
    }

    BackHandler(enabled = true) {
        commitDraftToViewModel()
        viewModel.previousOnboardingStep(onExitToSignIn = onSignOut)
    }

    LaunchedEffect(step) {
        scrollState.animateScrollTo(0)
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding()
            .verticalScroll(scrollState)
            .padding(horizontal = 20.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.Start
    ) {
        // 1. Navigation Top Bar with Back Button & Step Indicator
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color.White.copy(alpha = 0.14f))
                    .border(1.dp, Color.White.copy(alpha = 0.25f), RoundedCornerShape(12.dp))
                    .clickable {
                        commitDraftToViewModel()
                        viewModel.previousOnboardingStep(onExitToSignIn = onSignOut)
                    }
                    .padding(horizontal = 12.dp, vertical = 8.dp)
                    .testTag("onboarding_back_button")
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = Color.White,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = if (step == 1) "Sign in" else "Back",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White
                )
            }

            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(MintAccent.copy(alpha = 0.18f))
                    .border(1.dp, MintAccent.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Text(
                    text = "Step $step of 3",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = MintAccent
                )
            }
        }

        // 2. Step progress indicator bars
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            for (i in 1..3) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(5.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(
                            if (i <= step) MintAccent else Color.White.copy(alpha = 0.25f)
                        )
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        when (step) {
            1 -> {
                // Step 1: Firebase Phone OTP Verification
                Text(
                    text = "Welcome 👋",
                    fontSize = 32.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Text(
                    text = "Verify your mobile number via Firebase SMS to get started.",
                    fontSize = 14.sp,
                    color = Color.White.copy(alpha = 0.74f),
                    modifier = Modifier.padding(top = 4.dp, bottom = 16.dp)
                )

                GlassBox(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(145.dp),
                    cornerRadius = 22.dp
                ) {
                    AsyncImage(
                        model = R.drawable.nutrimate_hero_banner_1793444398689,
                        contentDescription = "NutriMate Lifestyle",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                GlassBox(
                    modifier = Modifier.fillMaxWidth(),
                    cornerRadius = 24.dp
                ) {
                    Column(
                        modifier = Modifier.padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Phone,
                                contentDescription = null,
                                tint = MintAccent,
                                modifier = Modifier.size(20.dp)
                            )
                            Text(
                                text = "Firebase Phone Verification",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }

                        val e164Preview = remember(phoneText) { viewModel.formatPhoneE164(phoneText) }

                        ModernOnboardingField(
                            value = phoneText,
                            onValueChange = {
                                phoneText = it
                                viewModel.clearPhoneAuthMessages()
                            },
                            label = "Mobile Number",
                            placeholder = "01712 345 678",
                            prefixBadge = if (!phoneText.trim().startsWith("+")) "+880" else null,
                            leadingIcon = Icons.Default.Phone,
                            helperText = if (phoneText.isNotBlank() && e164Preview.isNotBlank()) {
                                "Will send SMS to $e164Preview"
                            } else {
                                "Bangladesh (+880) or international (+country code)"
                            },
                            keyboardType = KeyboardType.Phone,
                            imeAction = ImeAction.Done,
                            onImeAction = { focusManager.clearFocus() },
                            testTag = "phone_input"
                        )

                        val canResend = uiState.phoneVerificationId != null
                        val onCooldown = uiState.resendCooldownSeconds > 0
                        Button(
                            onClick = {
                                focusManager.clearFocus()
                                commitDraftToViewModel()
                                if (activity != null) {
                                    viewModel.sendFirebasePhoneVerification(
                                        activity = activity,
                                        rawPhoneNumber = phoneText,
                                        isResend = canResend
                                    )
                                }
                            },
                            enabled = !uiState.isSendingOtp && !uiState.isVerifyingOtp && phoneText.isNotBlank() && !onCooldown,
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MintAccent.copy(alpha = 0.22f),
                                contentColor = MintAccent,
                                disabledContainerColor = Color.White.copy(alpha = 0.08f),
                                disabledContentColor = Color.White.copy(alpha = 0.4f)
                            ),
                            border = BorderStroke(
                                1.dp,
                                if (!onCooldown && phoneText.isNotBlank()) MintAccent.copy(alpha = 0.55f)
                                else Color.White.copy(alpha = 0.14f)
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .testTag("send_code_btn")
                        ) {
                            if (uiState.isSendingOtp) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    strokeWidth = 2.dp,
                                    color = MintAccent
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Sending OTP via Firebase…", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                            } else {
                                Icon(
                                    imageVector = Icons.Default.Sms,
                                    contentDescription = null,
                                    modifier = Modifier.size(17.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                val btnLabel = when {
                                    onCooldown -> "Resend OTP in ${uiState.resendCooldownSeconds}s"
                                    canResend -> "Resend OTP Code"
                                    else -> "Send 6-Digit OTP"
                                }
                                Text(btnLabel, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            }
                        }

                        // Inline status / error banners
                        AnimatedVisibility(visible = !uiState.phoneAuthError.isNullOrBlank()) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(Color(0x33FF5252))
                                    .border(1.dp, Color(0x88FF6B6B), RoundedCornerShape(12.dp))
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ErrorOutline,
                                    contentDescription = "Error",
                                    tint = Color(0xFFFF8A80),
                                    modifier = Modifier.size(18.dp)
                                )
                                Text(
                                    text = uiState.phoneAuthError.orEmpty(),
                                    fontSize = 12.sp,
                                    color = Color(0xFFFFCDD2),
                                    lineHeight = 16.sp
                                )
                            }
                        }

                        AnimatedVisibility(visible = !uiState.phoneAuthStatus.isNullOrBlank()) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(MintAccent.copy(alpha = 0.14f))
                                    .border(1.dp, MintAccent.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = "Status",
                                    tint = MintAccentGlow,
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = uiState.phoneAuthStatus.orEmpty(),
                                    fontSize = 12.sp,
                                    color = Color(0xFFB9FFD7),
                                    lineHeight = 16.sp
                                )
                            }
                        }

                        // Modern 6-digit Segmented OTP Pin Input
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                text = "6-DIGIT VERIFICATION CODE",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White.copy(alpha = 0.7f),
                                letterSpacing = 0.8.sp
                            )
                            ModernOtpPinField(
                                otp = otpText,
                                onOtpChange = { updated ->
                                    otpText = updated
                                    viewModel.clearPhoneAuthMessages()
                                },
                                isError = !uiState.phoneAuthError.isNullOrBlank() && otpText.length == 6,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("otp_input")
                            )
                        }

                        if (goals.isPhoneVerified) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    tint = MintAccent,
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = "Phone verified & linked with Firebase",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MintAccent
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))

                Button(
                    onClick = {
                        focusManager.clearFocus()
                        commitDraftToViewModel()
                        viewModel.verifyOtpAndContinue(phoneText, otpText)
                    },
                    enabled = !uiState.isVerifyingOtp,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MintAccent,
                        contentColor = MintAccentDark
                    ),
                    shape = RoundedCornerShape(18.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp)
                        .testTag("verify_continue_btn")
                ) {
                    if (uiState.isVerifyingOtp) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp,
                            color = MintAccentDark
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text("Verifying OTP…", fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    } else {
                        Text(
                            text = if (goals.isPhoneVerified) "Continue" else "Verify OTP & Continue",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    TextButton(
                        onClick = {
                            focusManager.clearFocus()
                            commitDraftToViewModel()
                            viewModel.skipPhoneVerification(phoneText)
                        },
                        modifier = Modifier.testTag("skip_phone_btn")
                    ) {
                        Text(
                            text = "Skip phone verification for now",
                            fontSize = 13.sp,
                            color = Color.White.copy(alpha = 0.68f)
                        )
                    }
                }
            }

            2 -> {
                // Step 2: About you (Clean blank fields + modern placeholders + quick city chips)
                Text(
                    text = "About you",
                    fontSize = 30.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Text(
                    text = "Fill in your profile so we can calculate your daily calorie target.",
                    fontSize = 14.sp,
                    color = Color.White.copy(alpha = 0.74f),
                    modifier = Modifier.padding(top = 4.dp, bottom = 18.dp)
                )

                val nameError = step2ValidationAttempted && nameText.trim().isBlank()
                val ageVal = ageText.toIntOrNull()
                val ageError = step2ValidationAttempted && (ageVal == null || ageVal !in 10..120)
                val heightVal = heightText.toIntOrNull()
                val heightError = step2ValidationAttempted && (heightVal == null || heightVal !in 50..300)
                val weightVal = weightText.toIntOrNull()
                val weightError = step2ValidationAttempted && (weightVal == null || weightVal !in 20..400)

                GlassBox(
                    modifier = Modifier.fillMaxWidth(),
                    cornerRadius = 24.dp
                ) {
                    Column(
                        modifier = Modifier.padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        ModernOnboardingField(
                            value = nameText,
                            onValueChange = { nameText = it },
                            label = "Full Name",
                            placeholder = "e.g. Rafiqul Islam",
                            leadingIcon = Icons.Default.Person,
                            isError = nameError,
                            helperText = if (nameError) "Please enter your name" else null,
                            capitalization = KeyboardCapitalization.Words,
                            imeAction = ImeAction.Next,
                            onImeAction = { focusManager.moveFocus(FocusDirection.Next) },
                            testTag = "name_input"
                        )

                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            ModernOnboardingField(
                                value = ageText,
                                onValueChange = { v -> ageText = v.filter { it.isDigit() }.take(3) },
                                label = "Age",
                                placeholder = "21",
                                unitSuffix = "yrs",
                                isError = ageError,
                                helperText = if (ageError) "10–120" else null,
                                keyboardType = KeyboardType.Number,
                                imeAction = ImeAction.Next,
                                onImeAction = { focusManager.moveFocus(FocusDirection.Next) },
                                modifier = Modifier.weight(1f),
                                testTag = "age_input"
                            )
                            ModernOnboardingField(
                                value = cityText,
                                onValueChange = { cityText = it },
                                label = "City",
                                placeholder = "Dhaka",
                                leadingIcon = Icons.Default.LocationOn,
                                capitalization = KeyboardCapitalization.Words,
                                imeAction = ImeAction.Next,
                                onImeAction = { focusManager.moveFocus(FocusDirection.Next) },
                                modifier = Modifier.weight(1.3f),
                                testTag = "city_input"
                            )
                        }

                        // Quick city chips for instant tap-fill
                        QuickFillChipRow(
                            options = listOf("Dhaka", "Khulna", "Rajshahi", "Chittagong", "Sylhet"),
                            selectedValue = cityText,
                            onSelect = { cityText = it }
                        )

                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            ModernOnboardingField(
                                value = heightText,
                                onValueChange = { v -> heightText = v.filter { it.isDigit() }.take(3) },
                                label = "Height",
                                placeholder = "172",
                                unitSuffix = "cm",
                                isError = heightError,
                                helperText = if (heightError) "50–300" else null,
                                keyboardType = KeyboardType.Number,
                                imeAction = ImeAction.Next,
                                onImeAction = { focusManager.moveFocus(FocusDirection.Next) },
                                modifier = Modifier.weight(1f),
                                testTag = "height_input"
                            )
                            ModernOnboardingField(
                                value = weightText,
                                onValueChange = { v -> weightText = v.filter { it.isDigit() }.take(3) },
                                label = "Weight",
                                placeholder = "64",
                                unitSuffix = "kg",
                                isError = weightError,
                                helperText = if (weightError) "20–400" else null,
                                keyboardType = KeyboardType.Number,
                                imeAction = ImeAction.Done,
                                onImeAction = { focusManager.clearFocus() },
                                modifier = Modifier.weight(1f),
                                testTag = "weight_input"
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))
                GlassToggleRow(
                    title = "🥦 I'm vegetarian",
                    isChecked = isVegetarian,
                    onCheckedChange = { isVegetarian = it },
                    testTag = "veg_toggle"
                )
                Spacer(modifier = Modifier.height(10.dp))
                GlassToggleRow(
                    title = "📍 Allow location access",
                    isChecked = allowLocation,
                    onCheckedChange = { allowLocation = it },
                    testTag = "location_toggle"
                )

                Spacer(modifier = Modifier.height(20.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            commitDraftToViewModel()
                            viewModel.setOnboardingStep(1)
                        },
                        modifier = Modifier
                            .weight(1f)
                            .height(52.dp)
                            .testTag("step2_back_btn"),
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.35f))
                    ) {
                        Text("Back", fontWeight = FontWeight.SemiBold)
                    }

                    Box(modifier = Modifier.weight(1.5f)) {
                        GlassButton(
                            text = "Continue",
                            onClick = {
                                step2ValidationAttempted = true
                                val valid = nameText.trim().isNotBlank() &&
                                    ageText.toIntOrNull() in 10..120 &&
                                    heightText.toIntOrNull() in 50..300 &&
                                    weightText.toIntOrNull() in 20..400
                                if (valid) {
                                    focusManager.clearFocus()
                                    commitDraftToViewModel()
                                    viewModel.setOnboardingStep(3)
                                }
                            },
                            testTag = "about_continue_btn"
                        )
                    }
                }
            }

            3 -> {
                // Step 3: Goals & Build My Month
                Text(
                    text = "Your goals",
                    fontSize = 30.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Text(
                    text = "Set your campus and monthly food budget to build your meal plan.",
                    fontSize = 14.sp,
                    color = Color.White.copy(alpha = 0.74f),
                    modifier = Modifier.padding(top = 4.dp, bottom = 18.dp)
                )

                GlassToggleRow(
                    title = "🏋️ I go to the gym",
                    isChecked = goesToGym,
                    onCheckedChange = { goesToGym = it },
                    testTag = "gym_toggle"
                )

                Spacer(modifier = Modifier.height(10.dp))

                GlassBox(
                    modifier = Modifier.fillMaxWidth(),
                    cornerRadius = 20.dp
                ) {
                    Column(modifier = Modifier.padding(18.dp)) {
                        Text(
                            text = if (hasCompleteBodyMetrics) {
                                "Recommended daily goal (Mifflin–St Jeor)"
                            } else {
                                "Estimated daily goal (Mifflin–St Jeor)"
                            },
                            fontSize = 12.sp,
                            color = Color.White.copy(alpha = 0.75f)
                        )
                        Row(
                            verticalAlignment = Alignment.Bottom,
                            modifier = Modifier.padding(top = 4.dp)
                        ) {
                            Text(
                                text = "%,d".format(liveCalories),
                                fontSize = 34.sp,
                                fontWeight = FontWeight.Bold,
                                color = MintAccent
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "kcal / day",
                                fontSize = 14.sp,
                                color = Color.White.copy(alpha = 0.72f),
                                modifier = Modifier.padding(bottom = 5.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                GlassToggleRow(
                    title = "🎓 I'm a student",
                    isChecked = isStudent,
                    onCheckedChange = { isStudent = it },
                    testTag = "student_toggle"
                )

                val budgetVal = budgetText.toIntOrNull()
                val budgetError = step3ValidationAttempted && isStudent && (budgetVal == null || budgetVal < 500)

                if (isStudent) {
                    Spacer(modifier = Modifier.height(10.dp))
                    GlassBox(
                        modifier = Modifier.fillMaxWidth(),
                        cornerRadius = 22.dp
                    ) {
                        Column(
                            modifier = Modifier.padding(18.dp),
                            verticalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            ModernOnboardingField(
                                value = universityText,
                                onValueChange = { universityText = it },
                                label = "University / Campus",
                                placeholder = "e.g. KUET, BUET, DU",
                                leadingIcon = Icons.Default.School,
                                capitalization = KeyboardCapitalization.Characters,
                                imeAction = ImeAction.Next,
                                onImeAction = { focusManager.moveFocus(FocusDirection.Next) },
                                testTag = "university_input"
                            )

                            QuickFillChipRow(
                                options = listOf("KUET", "BUET", "DU", "RUET", "CUET", "SUST", "JU"),
                                selectedValue = universityText,
                                onSelect = { universityText = it }
                            )

                            ModernOnboardingField(
                                value = budgetText,
                                onValueChange = { v -> budgetText = v.filter { it.isDigit() }.take(6) },
                                label = "Monthly Food Budget",
                                placeholder = "6000",
                                prefixBadge = "৳",
                                unitSuffix = "/ mo",
                                isError = budgetError,
                                helperText = if (budgetError) "Enter a monthly budget (min ৳500)" else null,
                                keyboardType = KeyboardType.Number,
                                imeAction = ImeAction.Done,
                                onImeAction = { focusManager.clearFocus() },
                                testTag = "budget_input"
                            )

                            QuickFillChipRow(
                                options = listOf("4000", "6000", "8000", "10000"),
                                labelTransform = { "৳%,d".format(it.toInt()) },
                                selectedValue = budgetText,
                                onSelect = { budgetText = it }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                GlassToggleRow(
                    title = "🍛 Already on a hall / mess plan",
                    isChecked = onMessPlan,
                    onCheckedChange = { onMessPlan = it },
                    testTag = "mess_toggle"
                )

                Spacer(modifier = Modifier.height(20.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            commitDraftToViewModel()
                            viewModel.setOnboardingStep(2)
                        },
                        modifier = Modifier
                            .weight(1f)
                            .height(52.dp)
                            .testTag("step3_back_btn"),
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.35f))
                    ) {
                        Text("Back", fontWeight = FontWeight.SemiBold)
                    }

                    Box(modifier = Modifier.weight(2f)) {
                        GlassButton(
                            text = "Build my month ✨",
                            onClick = {
                                step3ValidationAttempted = true
                                val budgetOk = !isStudent || (budgetText.toIntOrNull()?.let { it >= 500 } == true)
                                if (budgetOk) {
                                    focusManager.clearFocus()
                                    commitDraftToViewModel()
                                    viewModel.completeOnboarding()
                                }
                            },
                            testTag = "build_month_btn"
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun QuickFillChipRow(
    options: List<String>,
    selectedValue: String,
    onSelect: (String) -> Unit,
    labelTransform: (String) -> String = { it }
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        options.forEach { option ->
            val isSelected = selectedValue.equals(option, ignoreCase = true)
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (isSelected) MintAccent.copy(alpha = 0.25f) else Color.White.copy(alpha = 0.08f))
                    .border(
                        1.dp,
                        if (isSelected) MintAccent else Color.White.copy(alpha = 0.16f),
                        RoundedCornerShape(12.dp)
                    )
                    .clickable { onSelect(option) }
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Text(
                    text = labelTransform(option),
                    fontSize = 12.sp,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                    color = if (isSelected) MintAccentGlow else Color.White.copy(alpha = 0.82f)
                )
            }
        }
    }
}

@Composable
private fun ModernOtpPinField(
    otp: String,
    onOtpChange: (String) -> Unit,
    isError: Boolean = false,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

    Box(modifier = modifier) {
        BasicTextField(
            value = otp,
            onValueChange = { raw ->
                val filtered = raw.filter { it.isDigit() }.take(6)
                onOtpChange(filtered)
            },
            interactionSource = interactionSource,
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Number,
                imeAction = ImeAction.Done
            ),
            singleLine = true,
            cursorBrush = SolidColor(Color.Transparent),
            textStyle = TextStyle(color = Color.Transparent, fontSize = 1.sp),
            decorationBox = {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    for (index in 0 until 6) {
                        val char = otp.getOrNull(index)
                        val isActiveSlot = isFocused && (index == otp.length.coerceAtMost(5))
                        val borderColor by animateColorAsState(
                            targetValue = when {
                                isError -> Color(0xFFFF6B6B)
                                isActiveSlot -> MintAccent
                                char != null -> MintAccent.copy(alpha = 0.65f)
                                else -> Color.White.copy(alpha = 0.2f)
                            },
                            animationSpec = tween(180),
                            label = "otp_box_border_$index"
                        )
                        val bgColor by animateColorAsState(
                            targetValue = when {
                                isActiveSlot -> Color.White.copy(alpha = 0.14f)
                                char != null -> MintAccent.copy(alpha = 0.10f)
                                else -> Color.White.copy(alpha = 0.06f)
                            },
                            animationSpec = tween(180),
                            label = "otp_box_bg_$index"
                        )

                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(54.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .background(bgColor)
                                .border(
                                    width = if (isActiveSlot) 1.5.dp else 1.dp,
                                    color = borderColor,
                                    shape = RoundedCornerShape(14.dp)
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = char?.toString() ?: "•",
                                fontSize = if (char != null) 20.sp else 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (char != null) Color.White else Color.White.copy(alpha = 0.25f),
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            },
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
fun ModernOnboardingField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    placeholder: String,
    modifier: Modifier = Modifier,
    prefixBadge: String? = null,
    unitSuffix: String? = null,
    leadingIcon: ImageVector? = null,
    helperText: String? = null,
    isError: Boolean = false,
    keyboardType: KeyboardType = KeyboardType.Text,
    capitalization: KeyboardCapitalization = KeyboardCapitalization.None,
    imeAction: ImeAction = ImeAction.Next,
    onImeAction: () -> Unit = {},
    testTag: String = "modern_onboarding_field"
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

    val borderColor by animateColorAsState(
        targetValue = when {
            isError -> Color(0xFFFF6B6B)
            isFocused -> MintAccent
            value.isNotBlank() -> Color.White.copy(alpha = 0.35f)
            else -> Color.White.copy(alpha = 0.18f)
        },
        animationSpec = tween(180),
        label = "field_border"
    )
    val containerColor by animateColorAsState(
        targetValue = when {
            isFocused -> Color.White.copy(alpha = 0.12f)
            else -> Color.White.copy(alpha = 0.06f)
        },
        animationSpec = tween(180),
        label = "field_bg"
    )

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        Text(
            text = label.uppercase(),
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = when {
                isError -> Color(0xFFFF8A80)
                isFocused -> MintAccentGlow
                else -> Color.White.copy(alpha = 0.7f)
            },
            letterSpacing = 0.7.sp
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 52.dp)
                .clip(RoundedCornerShape(15.dp))
                .background(containerColor)
                .border(
                    width = if (isFocused || isError) 1.5.dp else 1.dp,
                    color = borderColor,
                    shape = RoundedCornerShape(15.dp)
                )
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (prefixBadge != null) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color.White.copy(alpha = 0.12f))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = prefixBadge,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = MintAccentGlow
                    )
                }
            } else if (leadingIcon != null) {
                Icon(
                    imageVector = leadingIcon,
                    contentDescription = null,
                    tint = if (isFocused) MintAccent else Color.White.copy(alpha = 0.5f),
                    modifier = Modifier.size(18.dp)
                )
            }

            Box(
                modifier = Modifier.weight(1f),
                contentAlignment = Alignment.CenterStart
            ) {
                if (value.isEmpty()) {
                    Text(
                        text = placeholder,
                        fontSize = 15.sp,
                        color = Color.White.copy(alpha = 0.34f),
                        maxLines = 1
                    )
                }
                BasicTextField(
                    value = value,
                    onValueChange = onValueChange,
                    singleLine = true,
                    interactionSource = interactionSource,
                    textStyle = TextStyle(
                        color = Color.White,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium
                    ),
                    cursorBrush = SolidColor(MintAccent),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = keyboardType,
                        capitalization = capitalization,
                        imeAction = imeAction
                    ),
                    keyboardActions = KeyboardActions(
                        onNext = { onImeAction() },
                        onDone = { onImeAction() }
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag(testTag)
                )
            }

            if (unitSuffix != null) {
                Text(
                    text = unitSuffix,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White.copy(alpha = 0.5f)
                )
            }

            if (isFocused && value.isNotEmpty()) {
                Box(
                    modifier = Modifier
                        .size(22.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.15f))
                        .clickable { onValueChange("") },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Clear",
                        tint = Color.White.copy(alpha = 0.85f),
                        modifier = Modifier.size(13.dp)
                    )
                }
            }
        }

        if (!helperText.isNullOrBlank()) {
            Text(
                text = helperText,
                fontSize = 11.sp,
                color = if (isError) Color(0xFFFF8A80) else Color.White.copy(alpha = 0.58f),
                modifier = Modifier.padding(start = 2.dp)
            )
        }
    }
}
