package com.example.nutrimate.ui.screens

import android.app.Activity
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.nutrimate.R
import com.example.nutrimate.ui.components.GlassBox
import com.example.nutrimate.ui.components.GlassButton
import com.example.nutrimate.ui.components.GlassToggleRow
import com.example.nutrimate.ui.theme.*
import com.example.nutrimate.viewmodel.NutriMateViewModel

@Composable
fun OnboardingScreen(
    viewModel: NutriMateViewModel,
    onSignOut: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val activity = context as? Activity
    val uiState by viewModel.uiState.collectAsState()
    val goals = uiState.userGoals
    val step = uiState.onboardingStep
    val scrollState = rememberScrollState()

    // Local fast-response field state so typing never lags or triggers network writes per keystroke
    var phoneText by rememberSaveable { mutableStateOf(goals.phone) }
    var otpText by rememberSaveable { mutableStateOf(goals.otp) }
    var nameText by rememberSaveable { mutableStateOf(goals.name) }
    var ageText by rememberSaveable { mutableStateOf(goals.age.toString()) }
    var cityText by rememberSaveable { mutableStateOf(goals.city) }
    var heightText by rememberSaveable { mutableStateOf(goals.heightCm.toString()) }
    var weightText by rememberSaveable { mutableStateOf(goals.weightKg.toString()) }
    var universityText by rememberSaveable { mutableStateOf(goals.university) }
    var budgetText by rememberSaveable { mutableStateOf(goals.monthlyBudget.toString()) }

    var isVegetarian by rememberSaveable { mutableStateOf(goals.isVegetarian) }
    var allowLocation by rememberSaveable { mutableStateOf(goals.allowLocation) }
    var goesToGym by rememberSaveable { mutableStateOf(goals.goesToGym) }
    var isStudent by rememberSaveable { mutableStateOf(goals.isStudent) }
    var onMessPlan by rememberSaveable { mutableStateOf(goals.onMessPlan) }

    // Sync auto-detected SMS OTP or loaded Firebase profile name once if still on defaults
    LaunchedEffect(goals.otp) {
        if (goals.isPhoneVerified && goals.otp.isNotBlank()) {
            otpText = goals.otp
        }
    }
    LaunchedEffect(goals.name) {
        if (nameText == "Dhruv" || nameText == "Student") {
            if (goals.name.isNotBlank()) nameText = goals.name
        }
    }

    // Helper to flush local draft inputs into ViewModel memory (zero Firestore lag)
    fun commitDraftToViewModel() {
        val parsedAge = ageText.toIntOrNull()?.coerceIn(10, 120) ?: goals.age
        val parsedHeight = heightText.toIntOrNull()?.coerceIn(50, 300) ?: goals.heightCm
        val parsedWeight = weightText.toIntOrNull()?.coerceIn(20, 400) ?: goals.weightKg
        val parsedBudget = budgetText.toIntOrNull()?.coerceAtLeast(0) ?: goals.monthlyBudget
        viewModel.updateGoalsDraft { current ->
            val updated = current.copy(
                phone = phoneText.trim(),
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

    // Live Mifflin-St Jeor calculation from current field values
    val liveCalories = remember(ageText, heightText, weightText, goesToGym) {
        val a = ageText.toIntOrNull()?.coerceIn(10, 120) ?: goals.age
        val h = heightText.toIntOrNull()?.coerceIn(50, 300) ?: goals.heightCm
        val w = weightText.toIntOrNull()?.coerceIn(20, 400) ?: goals.weightKg
        val bmr = 10 * w + 6.25 * h - 5 * a + 5
        val mult = if (goesToGym) 1.55 else 1.375
        (bmr * mult).toInt().coerceIn(500, 10000)
    }

    // System back button works consistently throughout onboarding steps
    BackHandler(enabled = true) {
        commitDraftToViewModel()
        viewModel.previousOnboardingStep(onExitToSignIn = onSignOut)
    }

    // Scroll to top when switching steps
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

            // Step Indicator Badge
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

        Spacer(modifier = Modifier.height(16.dp))

        when (step) {
            1 -> {
                // Step 1: Phone verification powered by Firebase
                Text(
                    text = "Welcome 👋",
                    fontSize = 32.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Text(
                    text = "Eat well. Spend smart. Verify your phone with Firebase.",
                    fontSize = 14.sp,
                    color = Color.White.copy(alpha = 0.72f),
                    modifier = Modifier.padding(top = 4.dp, bottom = 18.dp)
                )

                // Hero Illustration
                GlassBox(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(155.dp),
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
                        verticalArrangement = Arrangement.spacedBy(12.dp)
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

                        StyledTextField(
                            value = phoneText,
                            onValueChange = { phoneText = it },
                            label = "Phone number (+880...)",
                            keyboardType = KeyboardType.Phone,
                            testTag = "phone_input"
                        )

                        // Send Firebase SMS Code button
                        Button(
                            onClick = {
                                commitDraftToViewModel()
                                if (activity != null) {
                                    viewModel.sendFirebasePhoneVerification(activity, phoneText)
                                }
                            },
                            enabled = !uiState.isSendingOtp && phoneText.isNotBlank(),
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MintAccent.copy(alpha = 0.25f),
                                contentColor = MintAccent
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(46.dp)
                                .testTag("send_code_btn")
                        ) {
                            if (uiState.isSendingOtp) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    strokeWidth = 2.dp,
                                    color = MintAccent
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Sending SMS code…", fontSize = 13.sp)
                            } else {
                                Text("Send Code via Firebase SMS", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                            }
                        }

                        if (!uiState.phoneAuthStatus.isNullOrBlank()) {
                            Text(
                                text = uiState.phoneAuthStatus ?: "",
                                fontSize = 12.sp,
                                color = Color(0xFFB9FFD7),
                                lineHeight = 16.sp
                            )
                        }

                        StyledTextField(
                            value = otpText,
                            onValueChange = { if (it.length <= 6) otpText = it.filter { c -> c.isDigit() } },
                            label = "6-digit OTP code",
                            keyboardType = KeyboardType.Number,
                            testTag = "otp_input"
                        )

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
                                    text = "Phone verified with Firebase",
                                    fontSize = 12.sp,
                                    color = MintAccent
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))
                GlassButton(
                    text = "Verify & continue",
                    onClick = {
                        commitDraftToViewModel()
                        viewModel.verifyOtpAndContinue(phoneText, otpText)
                    },
                    testTag = "verify_continue_btn"
                )
            }

            2 -> {
                // Step 2: About you
                Text(
                    text = "About you",
                    fontSize = 30.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Text(
                    text = "Used to accurately calculate your nutrition needs.",
                    fontSize = 14.sp,
                    color = Color.White.copy(alpha = 0.72f),
                    modifier = Modifier.padding(top = 4.dp, bottom = 18.dp)
                )

                GlassBox(
                    modifier = Modifier.fillMaxWidth(),
                    cornerRadius = 24.dp
                ) {
                    Column(
                        modifier = Modifier.padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        StyledTextField(
                            value = nameText,
                            onValueChange = { nameText = it },
                            label = "Full Name",
                            testTag = "name_input"
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            StyledTextField(
                                value = ageText,
                                onValueChange = { v -> ageText = v.filter { it.isDigit() }.take(3) },
                                label = "Age",
                                keyboardType = KeyboardType.Number,
                                modifier = Modifier.weight(1f),
                                testTag = "age_input"
                            )
                            StyledTextField(
                                value = cityText,
                                onValueChange = { cityText = it },
                                label = "City",
                                modifier = Modifier.weight(1f),
                                testTag = "city_input"
                            )
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            StyledTextField(
                                value = heightText,
                                onValueChange = { v -> heightText = v.filter { it.isDigit() }.take(3) },
                                label = "Height cm",
                                keyboardType = KeyboardType.Number,
                                modifier = Modifier.weight(1f),
                                testTag = "height_input"
                            )
                            StyledTextField(
                                value = weightText,
                                onValueChange = { v -> weightText = v.filter { it.isDigit() }.take(3) },
                                label = "Weight kg",
                                keyboardType = KeyboardType.Number,
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
                                commitDraftToViewModel()
                                viewModel.setOnboardingStep(3)
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
                    text = "Calculate your caloric needs and build your monthly meal plan.",
                    fontSize = 14.sp,
                    color = Color.White.copy(alpha = 0.72f),
                    modifier = Modifier.padding(top = 4.dp, bottom = 18.dp)
                )

                GlassToggleRow(
                    title = "🏋️ I go to the gym",
                    isChecked = goesToGym,
                    onCheckedChange = { goesToGym = it },
                    testTag = "gym_toggle"
                )

                Spacer(modifier = Modifier.height(10.dp))

                // Mifflin St Jeor Card
                GlassBox(
                    modifier = Modifier.fillMaxWidth(),
                    cornerRadius = 20.dp
                ) {
                    Column(modifier = Modifier.padding(18.dp)) {
                        Text(
                            text = "Recommended daily goal (Mifflin–St Jeor)",
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
                                text = "kcal",
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

                if (isStudent) {
                    Spacer(modifier = Modifier.height(10.dp))
                    GlassBox(
                        modifier = Modifier.fillMaxWidth(),
                        cornerRadius = 20.dp
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            StyledTextField(
                                value = universityText,
                                onValueChange = { universityText = it },
                                label = "University / Campus",
                                testTag = "university_input"
                            )
                            StyledTextField(
                                value = budgetText,
                                onValueChange = { v -> budgetText = v.filter { it.isDigit() }.take(6) },
                                label = "Monthly food budget (৳)",
                                keyboardType = KeyboardType.Number,
                                testTag = "budget_input"
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
                                commitDraftToViewModel()
                                viewModel.completeOnboarding()
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
fun StyledTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    keyboardType: KeyboardType = KeyboardType.Text,
    testTag: String = "styled_textfield"
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label, color = Color.White.copy(alpha = 0.7f), fontSize = 13.sp) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        colors = OutlinedTextFieldDefaults.colors(
            focusedTextColor = Color.White,
            unfocusedTextColor = Color.White,
            focusedBorderColor = MintAccent,
            unfocusedBorderColor = Color.White.copy(alpha = 0.35f),
            focusedContainerColor = GlassInputBg,
            unfocusedContainerColor = GlassInputBg
        ),
        shape = RoundedCornerShape(14.dp),
        modifier = modifier
            .fillMaxWidth()
            .testTag(testTag)
    )
}
