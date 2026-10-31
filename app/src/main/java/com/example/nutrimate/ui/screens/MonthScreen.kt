package com.example.nutrimate.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Savings
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
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
import com.example.nutrimate.ui.theme.*
import com.example.nutrimate.viewmodel.NutriMateViewModel

@Composable
fun MonthScreen(
    viewModel: NutriMateViewModel,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    val goals = uiState.userGoals
    val remaining = goals.monthlyBudget - uiState.totalSpentThisMonth

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
                    text = "MONTHLY BUDGET & PROGRESS",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White.copy(alpha = 0.72f),
                    letterSpacing = 1.sp
                )
                Text(
                    text = "October Overview",
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
        }

        // Summary Metric Cards
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                GlassBox(
                    modifier = Modifier.weight(1f),
                    cornerRadius = 20.dp
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Spent", fontSize = 12.sp, color = Color.White.copy(alpha = 0.7f))
                        Text(
                            "৳%,d".format(uiState.totalSpentThisMonth),
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Text(
                            "of ৳%,d".format(goals.monthlyBudget),
                            fontSize = 11.sp,
                            color = Color.White.copy(alpha = 0.6f)
                        )
                    }
                }

                GlassBox(
                    modifier = Modifier.weight(1f),
                    cornerRadius = 20.dp
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Remaining", fontSize = 12.sp, color = Color.White.copy(alpha = 0.7f))
                        Text(
                            "৳%,d".format(remaining),
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold,
                            color = MintAccent
                        )
                        Text("53% balance", fontSize = 11.sp, color = MintAccentGlow)
                    }
                }
            }
        }

        // Partner Savings Highlight Card
        item {
            GlassBox(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("savings_highlight_card"),
                cornerRadius = 22.dp
            ) {
                Row(
                    modifier = Modifier.padding(18.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(TagYellow.copy(alpha = 0.25f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Savings,
                            contentDescription = "Savings",
                            tint = TagYellow,
                            modifier = Modifier.size(26.dp)
                        )
                    }

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Student Partner Discounts",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Text(
                            text = "You've saved ৳%,d this month using QR partner deals at Star Kabab, Biryani Bhaban & Fuchka Corner.".format(uiState.totalSavedFromDiscounts),
                            fontSize = 12.sp,
                            color = Color.White.copy(alpha = 0.75f),
                            lineHeight = 16.sp
                        )
                    }
                }
            }
        }

        // Category Breakdown
        item {
            GlassBox(
                modifier = Modifier.fillMaxWidth(),
                cornerRadius = 22.dp
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "Spending by Meal Type",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )

                    MealSpendCategoryRow(name = "Lunch", percent = 40, emoji = "🍛", amount = "৳1,120", color = MintAccent)
                    MealSpendCategoryRow(name = "Dinner", percent = 25, emoji = "🍲", amount = "৳700", color = CyanAccent)
                    MealSpendCategoryRow(name = "Breakfast", percent = 20, emoji = "🍳", amount = "৳560", color = OrangeAccent)
                    MealSpendCategoryRow(name = "Snacks", percent = 15, emoji = "🥪", amount = "৳420", color = TagYellow)
                }
            }
        }
    }
}

@Composable
fun MealSpendCategoryRow(
    name: String,
    percent: Int,
    emoji: String,
    amount: String,
    color: Color
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("$emoji $name", fontSize = 14.sp, color = Color.White)
            Text("$amount ($percent%)", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(Color.White.copy(alpha = 0.15f))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(percent / 100f)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(3.dp))
                    .background(color)
            )
        }
    }
}
