package com.example.nutrimate.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.nutrimate.model.MealOption
import com.example.nutrimate.model.MealSlot
import com.example.nutrimate.ui.components.DayProgressRing
import com.example.nutrimate.ui.components.GlassBox
import com.example.nutrimate.ui.theme.*
import com.example.nutrimate.viewmodel.NutriMateViewModel
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

@Composable
fun HomeScreen(
    viewModel: NutriMateViewModel,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    val goals = uiState.userGoals

    val calendar = remember { Calendar.getInstance() }
    val hour = calendar.get(Calendar.HOUR_OF_DAY)
    val dayProgressPercent = ((hour.toFloat() / 24f) * 100).toInt().coerceIn(5, 100)

    val greetingText = remember(hour, goals.name) {
        val period = when {
            hour < 12 -> "Good morning"
            hour < 17 -> "Good afternoon"
            else -> "Good evening"
        }
        "$period, ${goals.name}"
    }

    val dateFormatted = remember {
        val sdf = SimpleDateFormat("EEEE, MMMM d", Locale.getDefault())
        sdf.format(calendar.time).uppercase()
    }

    val monthName = remember {
        SimpleDateFormat("MMMM", Locale.getDefault()).format(calendar.time)
    }

    val remainingMonthlyBudget = goals.monthlyBudget - uiState.totalSpentThisMonth

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 18.dp),
        contentPadding = PaddingValues(top = 18.dp, bottom = 100.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Column {
                Text(
                    text = dateFormatted,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White.copy(alpha = 0.72f),
                    letterSpacing = 1.sp
                )
                Text(
                    text = greetingText,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
        }

        // Hero Card
        item {
            GlassBox(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("hero_budget_card"),
                cornerRadius = 26.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(18.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(18.dp)
                ) {
                    DayProgressRing(progressPercent = dayProgressPercent)

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Today's budget",
                            fontSize = 12.sp,
                            color = Color.White.copy(alpha = 0.75f)
                        )
                        Text(
                            text = "৳${uiState.totalTodayBudget}",
                            fontSize = 32.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color.White
                        )
                        Text(
                            text = "${uiState.totalTodayKcal} kcal target",
                            fontSize = 13.sp,
                            color = Color.White.copy(alpha = 0.85f),
                            modifier = Modifier.padding(top = 2.dp)
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(top = 6.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(MintAccentGlow)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "On track this month",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                                color = MintAccentGlow
                            )
                        }
                    }
                }
            }
        }

        // Meal Slots
        items(uiState.mealSlots.size) { slotIndex ->
            val slot = uiState.mealSlots[slotIndex]
            val isExpanded = uiState.expandedMealIndex == slotIndex

            MealSlotCard(
                slot = slot,
                isExpanded = isExpanded,
                onHeaderClick = { viewModel.toggleMealExpansion(slotIndex) },
                onOptionSelect = { optIdx -> viewModel.selectMealOption(slotIndex, optIdx) },
                testTag = "meal_card_${slot.id}"
            )
        }

        // Monthly Budget Bar Chart Card
        item {
            GlassBox(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("monthly_budget_bars_card"),
                cornerRadius = 24.dp
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "$monthName budget",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Text(
                            text = "৳%,d · ৳%,d left".format(goals.monthlyBudget, remainingMonthlyBudget),
                            fontSize = 12.sp,
                            color = Color.White.copy(alpha = 0.72f)
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // 31 Daily spend bars
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(64.dp),
                        horizontalArrangement = Arrangement.spacedBy(3.dp),
                        verticalAlignment = Alignment.Bottom
                    ) {
                        uiState.monthlySpends.forEach { daySpend ->
                            val heightFraction = (daySpend.spent / 230f).coerceIn(0.2f, 1f)
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight(heightFraction)
                                    .clip(RoundedCornerShape(3.dp))
                                    .background(
                                        if (daySpend.isCurrentDay) MintAccent else Color.White.copy(alpha = 0.35f)
                                    )
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun MealSlotCard(
    slot: MealSlot,
    isExpanded: Boolean,
    onHeaderClick: () -> Unit,
    onOptionSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    testTag: String = "meal_slot_card"
) {
    GlassBox(
        modifier = modifier
            .fillMaxWidth()
            .testTag(testTag),
        cornerRadius = 24.dp
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Header Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onHeaderClick),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = slot.emoji,
                    fontSize = 28.sp
                )

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = slot.name,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Text(
                        text = "৳${slot.budget} · ${slot.targetKcal} kcal",
                        fontSize = 13.sp,
                        color = Color.White.copy(alpha = 0.72f)
                    )
                }

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color.White.copy(alpha = 0.2f))
                        .padding(horizontal = 10.dp, vertical = 5.dp)
                ) {
                    Text(
                        text = if (isExpanded) "Options" else "Tap",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color.White
                    )
                }
            }

            // Expandable Meal Options List
            AnimatedVisibility(
                visible = isExpanded,
                enter = expandVertically(animationSpec = tween(300)),
                exit = shrinkVertically(animationSpec = tween(250))
            ) {
                Column(
                    modifier = Modifier.padding(top = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    slot.options.forEachIndexed { optIndex, option ->
                        val isSelected = slot.selectedOptionIndex == optIndex
                        MealOptionItem(
                            option = option,
                            emoji = slot.emoji,
                            isSelected = isSelected,
                            onClick = { onOptionSelect(optIndex) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun MealOptionItem(
    option: MealOption,
    emoji: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val bgModifier = if (option.isPartner) {
        Modifier.background(
            brush = Brush.horizontalGradient(
                colors = listOf(
                    MintAccent.copy(alpha = 0.35f),
                    Color.White.copy(alpha = 0.12f)
                )
            ),
            shape = RoundedCornerShape(18.dp)
        ).border(
            width = 1.dp,
            color = MintAccent.copy(alpha = 0.7f),
            shape = RoundedCornerShape(18.dp)
        )
    } else {
        Modifier.background(
            color = Color.White.copy(alpha = 0.1f),
            shape = RoundedCornerShape(18.dp)
        ).border(
            width = 1.dp,
            color = Color.White.copy(alpha = 0.16f),
            shape = RoundedCornerShape(18.dp)
        )
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(bgModifier)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(text = emoji, fontSize = 22.sp)

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = option.place,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
            Text(
                text = "${option.title} · ${option.priceStr}",
                fontSize = 12.sp,
                color = Color.White.copy(alpha = 0.72f)
            )
        }

        if (option.isPartner) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .background(TagYellow)
                    .padding(horizontal = 8.dp, vertical = 3.dp)
            ) {
                Text(
                    text = "${option.discountTag} PARTNER",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = TagYellowText
                )
            }
        }

        if (isSelected) {
            Icon(
                imageVector = Icons.Default.CheckCircle,
                contentDescription = "Selected",
                tint = MintAccent,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}
