package com.example.nutrimate.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.nutrimate.ui.components.GlassBox
import com.example.nutrimate.ui.components.GlassToggleRow
import com.example.nutrimate.ui.theme.*
import com.example.nutrimate.viewmodel.NutriMateViewModel

@Composable
fun ProfileScreen(
    viewModel: NutriMateViewModel,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    val goals = uiState.userGoals

    val bmi = remember(goals.heightCm, goals.weightKg) {
        val hMeter = goals.heightCm / 100f
        if (hMeter > 0) String.format("%.1f", goals.weightKg / (hMeter * hMeter)) else "21.6"
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 18.dp),
        contentPadding = PaddingValues(top = 18.dp, bottom = 100.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Column {
                Text(
                    text = "STUDENT PROFILE",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White.copy(alpha = 0.72f),
                    letterSpacing = 1.sp
                )
                Text(
                    text = goals.name,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
        }

        // Profile Avatar Card
        item {
            GlassBox(
                modifier = Modifier.fillMaxWidth(),
                cornerRadius = 24.dp
            ) {
                Row(
                    modifier = Modifier.padding(18.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(60.dp)
                            .clip(CircleShape)
                            .background(MintAccent.copy(alpha = 0.2f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Person,
                            contentDescription = "Avatar",
                            tint = MintAccent,
                            modifier = Modifier.size(36.dp)
                        )
                    }

                    Column {
                        Text(
                            text = "${goals.name} · ${goals.age} yrs",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Text(
                            text = "${goals.university} · ${goals.city}",
                            fontSize = 13.sp,
                            color = Color.White.copy(alpha = 0.72f)
                        )
                        Text(
                            text = goals.phone,
                            fontSize = 12.sp,
                            color = MintAccentGlow,
                            modifier = Modifier.padding(top = 2.dp)
                        )
                    }
                }
            }
        }

        // Body & Nutrition Metrics
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                MetricBox(label = "Height", value = "${goals.heightCm} cm", modifier = Modifier.weight(1f))
                MetricBox(label = "Weight", value = "${goals.weightKg} kg", modifier = Modifier.weight(1f))
                MetricBox(label = "BMI", value = bmi, modifier = Modifier.weight(1f))
            }
        }

        // Calorie Goal Card
        item {
            GlassBox(
                modifier = Modifier.fillMaxWidth(),
                cornerRadius = 22.dp
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Text(
                        text = "Calculated Daily Calorie Target (Mifflin–St Jeor)",
                        fontSize = 12.sp,
                        color = Color.White.copy(alpha = 0.75f)
                    )
                    Text(
                        text = "%,d kcal / day".format(goals.calculateMifflinStJeor()),
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        color = MintAccent,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }
        }

        // Dietary & Lifestyle Toggles
        item {
            Text(
                text = "Preferences & Lifestyle",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
        }

        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                GlassToggleRow(
                    title = "🥦 Vegetarian diet",
                    isChecked = goals.isVegetarian,
                    onCheckedChange = { v -> viewModel.updateGoals { it.copy(isVegetarian = v) } },
                    testTag = "pref_veg"
                )
                GlassToggleRow(
                    title = "🏋️ Gym & regular workout",
                    isChecked = goals.goesToGym,
                    onCheckedChange = { v ->
                        viewModel.updateGoals {
                            val updated = it.copy(goesToGym = v)
                            updated.copy(dailyCalorieGoal = updated.calculateMifflinStJeor())
                        }
                    },
                    testTag = "pref_gym"
                )
                GlassToggleRow(
                    title = "🎓 Student partner perks",
                    isChecked = goals.isStudent,
                    onCheckedChange = { v -> viewModel.updateGoals { it.copy(isStudent = v) } },
                    testTag = "pref_student"
                )
                GlassToggleRow(
                    title = "🍛 Hall / Mess Meal Plan",
                    isChecked = goals.onMessPlan,
                    onCheckedChange = { v -> viewModel.updateGoals { it.copy(onMessPlan = v) } },
                    testTag = "pref_mess"
                )
                GlassToggleRow(
                    title = "📍 Campus location access",
                    isChecked = goals.allowLocation,
                    onCheckedChange = { v -> viewModel.updateGoals { it.copy(allowLocation = v) } },
                    testTag = "pref_location"
                )
            }
        }

        // Account & Actions
        item {
            Spacer(modifier = Modifier.height(10.dp))
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(
                    onClick = { viewModel.restartOnboarding() },
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .testTag("restart_onboarding_btn")
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Restart",
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Re-run Onboarding Setup", fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                }

                Button(
                    onClick = {
                        com.google.firebase.auth.FirebaseAuth.getInstance().signOut()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color.White.copy(alpha = 0.15f)),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .testTag("sign_out_button")
                ) {
                    Text("Sign out of Google Account", color = Color(0xFFFF8A8A), fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

@Composable
fun MetricBox(label: String, value: String, modifier: Modifier = Modifier) {
    GlassBox(
        modifier = modifier,
        cornerRadius = 18.dp
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(label, fontSize = 11.sp, color = Color.White.copy(alpha = 0.7f))
            Text(value, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.White, modifier = Modifier.padding(top = 2.dp))
        }
    }
}
