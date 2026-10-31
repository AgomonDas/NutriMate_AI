package com.example.nutrimate.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
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
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    val goals = uiState.userGoals
    val step = uiState.onboardingStep
    val scrollState = rememberScrollState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(horizontal = 20.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.Start
    ) {
        // Step progress dots
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            for (i in 1..3) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(5.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(
                            if (i <= step) Color.White else Color.White.copy(alpha = 0.25f)
                        )
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        when (step) {
            1 -> {
                // Step 1: Phone verification
                Text(
                    text = "Welcome 👋",
                    fontSize = 32.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Text(
                    text = "Eat well. Spend smart. Sign up with your phone.",
                    fontSize = 14.sp,
                    color = Color.White.copy(alpha = 0.72f),
                    modifier = Modifier.padding(top = 4.dp, bottom = 18.dp)
                )

                // Hero Illustration
                GlassBox(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp),
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
                        StyledTextField(
                            value = goals.phone,
                            onValueChange = { p -> viewModel.updateGoals { it.copy(phone = p) } },
                            label = "Phone number",
                            keyboardType = KeyboardType.Phone,
                            testTag = "phone_input"
                        )
                        StyledTextField(
                            value = goals.otp,
                            onValueChange = { o -> viewModel.updateGoals { it.copy(otp = o) } },
                            label = "6-digit OTP",
                            keyboardType = KeyboardType.Number,
                            testTag = "otp_input"
                        )
                        Text(
                            text = "We'll text you a code. Standard SMS rates apply.",
                            fontSize = 12.sp,
                            color = Color.White.copy(alpha = 0.65f)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))
                GlassButton(
                    text = "Verify & continue",
                    onClick = { viewModel.setOnboardingStep(2) },
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
                            value = goals.name,
                            onValueChange = { v -> viewModel.updateGoals { it.copy(name = v) } },
                            label = "Full Name",
                            testTag = "name_input"
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            StyledTextField(
                                value = goals.age.toString(),
                                onValueChange = { v -> viewModel.updateGoals { it.copy(age = v.toIntOrNull() ?: 20) } },
                                label = "Age",
                                keyboardType = KeyboardType.Number,
                                modifier = Modifier.weight(1f),
                                testTag = "age_input"
                            )
                            StyledTextField(
                                value = goals.city,
                                onValueChange = { v -> viewModel.updateGoals { it.copy(city = v) } },
                                label = "City",
                                modifier = Modifier.weight(1f),
                                testTag = "city_input"
                            )
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            StyledTextField(
                                value = goals.heightCm.toString(),
                                onValueChange = { v -> viewModel.updateGoals { it.copy(heightCm = v.toIntOrNull() ?: 170) } },
                                label = "Height cm",
                                keyboardType = KeyboardType.Number,
                                modifier = Modifier.weight(1f),
                                testTag = "height_input"
                            )
                            StyledTextField(
                                value = goals.weightKg.toString(),
                                onValueChange = { v -> viewModel.updateGoals { it.copy(weightKg = v.toIntOrNull() ?: 60) } },
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
                    isChecked = goals.isVegetarian,
                    onCheckedChange = { v -> viewModel.updateGoals { it.copy(isVegetarian = v) } },
                    testTag = "veg_toggle"
                )
                Spacer(modifier = Modifier.height(10.dp))
                GlassToggleRow(
                    title = "📍 Allow location access",
                    isChecked = goals.allowLocation,
                    onCheckedChange = { v -> viewModel.updateGoals { it.copy(allowLocation = v) } },
                    testTag = "location_toggle"
                )

                Spacer(modifier = Modifier.height(20.dp))
                GlassButton(
                    text = "Continue",
                    onClick = { viewModel.setOnboardingStep(3) },
                    testTag = "about_continue_btn"
                )
            }

            3 -> {
                // Step 3: Goals
                Text(
                    text = "Your goals",
                    fontSize = 30.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Text(
                    text = "Skip the calorie goal and we'll suggest one automatically.",
                    fontSize = 14.sp,
                    color = Color.White.copy(alpha = 0.72f),
                    modifier = Modifier.padding(top = 4.dp, bottom = 18.dp)
                )

                GlassToggleRow(
                    title = "🏋️ I go to the gym",
                    isChecked = goals.goesToGym,
                    onCheckedChange = { v ->
                        viewModel.updateGoals {
                            val updated = it.copy(goesToGym = v)
                            updated.copy(dailyCalorieGoal = updated.calculateMifflinStJeor())
                        }
                    },
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
                                text = "%,d".format(goals.calculateMifflinStJeor()),
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
                    isChecked = goals.isStudent,
                    onCheckedChange = { v -> viewModel.updateGoals { it.copy(isStudent = v) } },
                    testTag = "student_toggle"
                )

                if (goals.isStudent) {
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
                                value = goals.university,
                                onValueChange = { u -> viewModel.updateGoals { it.copy(university = u) } },
                                label = "University / Campus",
                                testTag = "university_input"
                            )
                            StyledTextField(
                                value = goals.monthlyBudget.toString(),
                                onValueChange = { b -> viewModel.updateGoals { it.copy(monthlyBudget = b.toIntOrNull() ?: 5000) } },
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
                    isChecked = goals.onMessPlan,
                    onCheckedChange = { v -> viewModel.updateGoals { it.copy(onMessPlan = v) } },
                    testTag = "mess_toggle"
                )

                Spacer(modifier = Modifier.height(20.dp))
                GlassButton(
                    text = "Build my month ✨",
                    onClick = { viewModel.completeOnboarding() },
                    testTag = "build_month_btn"
                )
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
        shape = RoundedCornerShape(16.dp),
        modifier = modifier
            .fillMaxWidth()
            .testTag(testTag)
    )
}
