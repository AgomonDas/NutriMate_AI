package com.example.nutrimate.ui.screens

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.nutrimate.data.location.CampusArea
import com.example.nutrimate.data.location.CampusLocationManager
import com.example.nutrimate.data.model.PartnerRestaurantEntity
import com.example.nutrimate.data.model.RestaurantWithDistance
import com.example.nutrimate.model.MealOption
import com.example.nutrimate.model.MealSlot
import com.example.nutrimate.ui.components.*
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
    val feastState by viewModel.feastState.collectAsState()
    val canUndo by viewModel.canUndo.collectAsState()
    val goals = uiState.userGoals
    val context = LocalContext.current

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val fineGranted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] ?: false
        val coarseGranted = permissions[Manifest.permission.ACCESS_COARSE_LOCATION] ?: false
        if (fineGranted || coarseGranted) {
            viewModel.detectLocationAndSetCampus(context)
        }
    }

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

    Box(modifier = modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 18.dp),
            contentPadding = PaddingValues(top = 18.dp, bottom = 120.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = dateFormatted,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color.White.copy(alpha = 0.72f),
                            letterSpacing = 1.sp
                        )
                        Text(
                            text = "Daily Meal Plan",
                            fontSize = 26.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            modifier = Modifier.padding(top = 2.dp)
                        )
                    }

                    // Log Custom Expense FAB
                    Button(
                        onClick = { viewModel.openManualMealDialog() },
                        colors = ButtonDefaults.buttonColors(containerColor = MintAccent),
                        shape = RoundedCornerShape(16.dp),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                        modifier = Modifier.testTag("open_manual_log_btn")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "Log Expense",
                            tint = MintAccentDark,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Log Meal",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = MintAccentDark
                        )
                    }
                }
            }

            // Daily Allowance Hero Card
            item {
                GlassBox(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("hero_budget_card"),
                    cornerRadius = 24.dp
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
                                text = "Today's Daily Allowance",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
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
                                    text = "On track with daily budget",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = MintAccentGlow
                                )
                            }
                        }
                    }
                }
            }

            // FEAST RESERVE BUCKETS CARD
            item {
                FeastBucketCard(
                    state = feastState,
                    canUndo = canUndo,
                    onUndoClick = { viewModel.undoLastTradeoff() }
                )
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
                    onRequestUpgrade = {
                        // Demo trigger: Request Kacchi Biriyani (12,000 paisa / Tk 120) on lunch
                        viewModel.requestMealUpgrade(
                            mealId = slot.id,
                            newPricePaisa = if (slot.id == "lunch") 12_000L else (slot.budget + 40) * 100L,
                            itemName = if (slot.id == "lunch") "Kacchi Biriyani" else "Chef's Special Feast ${slot.name}",
                            placeName = "Star Kabab & Biriyani",
                            kcal = 850
                        )
                    },
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

        // Floating Undo Notification Banner
        AnimatedVisibility(
            visible = uiState.undoNoticeMessage != null,
            enter = slideInVertically(initialOffsetY = { -it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { -it }) + fadeOut(),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 16.dp, start = 18.dp, end = 18.dp)
        ) {
            uiState.undoNoticeMessage?.let { notice ->
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color(0xE6131F38))
                        .border(1.dp, MintAccent.copy(alpha = 0.6f), RoundedCornerShape(16.dp))
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = notice,
                            fontSize = 13.sp,
                            color = Color.White,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(
                            onClick = { viewModel.dismissUndoNotice() },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Dismiss",
                                tint = Color.White,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }
        }

        // Active Trade-off Proposal Dialog
        uiState.activeTradeoffProposal?.let { proposal ->
            TradeoffProposalDialog(
                proposal = proposal,
                onAccept = { viewModel.confirmTradeoffProposal() },
                onDecline = { viewModel.declineTradeoffProposal() }
            )
        }

        // Manual Meal Logging Dialog
        if (uiState.showManualMealDialog) {
            ManualMealEntryDialog(
                onDismiss = { viewModel.dismissManualMealDialog() },
                onSubmit = { restaurant, dish, spent, kcal, slotId ->
                    viewModel.logManualMeal(restaurant, dish, spent, kcal, slotId)
                }
            )
        }

        // Campus Area Selection Dialog
        if (uiState.showCampusSelectorDialog) {
            CampusAreaSelectorDialog(
                currentAreaId = uiState.currentCampusArea.id,
                areas = CampusLocationManager.CAMPUS_AREAS,
                isLocatingGps = uiState.isLocatingGps,
                onDismiss = { viewModel.dismissCampusSelector() },
                onSelectArea = { area -> viewModel.selectCampusArea(area) },
                onGpsDetectClick = {
                    permissionLauncher.launch(
                        arrayOf(
                            Manifest.permission.ACCESS_FINE_LOCATION,
                            Manifest.permission.ACCESS_COARSE_LOCATION
                        )
                    )
                }
            )
        }
    }
}

@Composable
fun MealSlotCard(
    slot: MealSlot,
    isExpanded: Boolean,
    onHeaderClick: () -> Unit,
    onOptionSelect: (Int) -> Unit,
    onRequestUpgrade: () -> Unit,
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
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = slot.name,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        val activeOpt = slot.activeOption
                        if (activeOpt.isPartner && activeOpt.discountTag.isNotBlank()) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(MintAccent.copy(alpha = 0.2f))
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = activeOpt.discountTag,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MintAccentGlow
                                )
                            }
                        }
                    }
                    Text(
                        text = "Allowance: ৳${slot.budget} · ${slot.targetKcal} kcal",
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
    val bgModifier = if (isSelected) {
        Modifier.background(
            color = MintAccent.copy(alpha = 0.22f),
            shape = RoundedCornerShape(16.dp)
        ).border(
            width = 1.dp,
            color = MintAccent.copy(alpha = 0.7f),
            shape = RoundedCornerShape(16.dp)
        )
    } else {
        Modifier.background(
            color = Color.White.copy(alpha = 0.08f),
            shape = RoundedCornerShape(16.dp)
        ).border(
            width = 1.dp,
            color = Color.White.copy(alpha = 0.14f),
            shape = RoundedCornerShape(16.dp)
        )
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(bgModifier)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(text = emoji, fontSize = 22.sp)

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = option.title,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color.White
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.padding(top = 2.dp)
            ) {
                Text(
                    text = option.place,
                    fontSize = 12.sp,
                    color = Color.White.copy(alpha = 0.7f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                if (option.isPartner && option.discountTag.isNotBlank()) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(MintAccent.copy(alpha = 0.2f))
                            .border(0.5.dp, MintAccent.copy(alpha = 0.55f), RoundedCornerShape(6.dp))
                            .padding(horizontal = 6.dp, vertical = 1.dp)
                    ) {
                        Text(
                            text = "${option.discountTag} off",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = MintAccentGlow
                        )
                    }
                }
            }
        }

        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = option.priceStr,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = if (isSelected) MintAccent else Color.White
            )
            if (option.isPartner && option.discountTag.isNotBlank()) {
                val pct = option.discountTag.filter { it.isDigit() }.toIntOrNull() ?: 10
                val origPrice = if (pct in 1..90) (option.priceNum * 100 / (100 - pct)) else (option.priceNum + 5)
                Text(
                    text = "৳$origPrice",
                    fontSize = 10.sp,
                    color = Color.White.copy(alpha = 0.45f),
                    textDecoration = androidx.compose.ui.text.style.TextDecoration.LineThrough
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

@Composable
fun CampusLocationCard(
    currentArea: CampusArea,
    partnerCount: Int,
    isLocatingGps: Boolean,
    onChangeClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    GlassBox(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onChangeClick)
            .testTag("campus_location_card"),
        cornerRadius = 20.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(MintAccent.copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.LocationOn,
                    contentDescription = "Location",
                    tint = MintAccent,
                    modifier = Modifier.size(22.dp)
                )
            }

            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = currentArea.name,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Text(
                    text = "${currentArea.description} · $partnerCount Partners",
                    fontSize = 12.sp,
                    color = Color.White.copy(alpha = 0.72f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Surface(
                color = MintAccent.copy(alpha = 0.25f),
                shape = RoundedCornerShape(12.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, MintAccent.copy(alpha = 0.6f))
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    if (isLocatingGps) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(12.dp),
                            color = MintAccent,
                            strokeWidth = 2.dp
                        )
                    }
                    Text(
                        text = if (isLocatingGps) "Locating..." else "Change",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = MintAccent
                    )
                    Icon(
                        imageVector = Icons.Default.KeyboardArrowDown,
                        contentDescription = "Select Area",
                        tint = MintAccent,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun NearbyPartnersRow(
    partners: List<PartnerRestaurantEntity>,
    nearbyWithDistance: List<RestaurantWithDistance> = emptyList(),
    modifier: Modifier = Modifier
) {
    val titleText = if (nearbyWithDistance.isNotEmpty()) "Nearby Campus Restaurants" else "Nearby Student Partners"
    val subtitleText = if (nearbyWithDistance.isNotEmpty()) "Real-time GPS · Student Discounts" else "Show Student ID for discount"

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 2.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = titleText,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White.copy(alpha = 0.9f)
            )
            Text(
                text = subtitleText,
                fontSize = 11.sp,
                color = TagYellowText
            )
        }

        Spacer(modifier = Modifier.height(6.dp))

        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (nearbyWithDistance.isNotEmpty()) {
                items(nearbyWithDistance) { item ->
                    val rest = item.restaurant
                    GlassBox(
                        modifier = Modifier
                            .width(210.dp)
                            .testTag("restaurant_chip_${rest.id}"),
                        cornerRadius = 16.dp
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = rest.name,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f)
                                )
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.Star,
                                        contentDescription = "Rating",
                                        tint = TagYellow,
                                        modifier = Modifier.size(12.dp)
                                    )
                                    Text(
                                        text = "${rest.rating}",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White.copy(alpha = 0.85f),
                                        modifier = Modifier.padding(start = 2.dp)
                                    )
                                }
                            }

                            Text(
                                text = rest.cuisine,
                                fontSize = 11.sp,
                                color = Color.White.copy(alpha = 0.65f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )

                            Spacer(modifier = Modifier.height(8.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(MintAccent.copy(alpha = 0.25f))
                                        .border(1.dp, MintAccent.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = rest.discountTag,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MintAccent
                                    )
                                }

                                Text(
                                    text = "📍 ${item.distanceFormatted}",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = Color.White.copy(alpha = 0.85f)
                                )
                            }
                        }
                    }
                }
            } else {
                items(partners) { partner ->
                    GlassBox(
                        modifier = Modifier
                            .width(200.dp)
                            .testTag("partner_chip_${partner.id}"),
                        cornerRadius = 16.dp
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = partner.name,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f)
                                )
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.Star,
                                        contentDescription = "Rating",
                                        tint = TagYellow,
                                        modifier = Modifier.size(12.dp)
                                    )
                                    Text(
                                        text = "${partner.rating}",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White.copy(alpha = 0.85f),
                                        modifier = Modifier.padding(start = 2.dp)
                                    )
                                }
                            }

                            Text(
                                text = partner.cuisine,
                                fontSize = 11.sp,
                                color = Color.White.copy(alpha = 0.65f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )

                            Spacer(modifier = Modifier.height(8.dp))

                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(MintAccent.copy(alpha = 0.25f))
                                    .border(1.dp, MintAccent.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = partner.discountTag,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MintAccent
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun CampusAreaSelectorDialog(
    currentAreaId: String,
    areas: List<CampusArea>,
    isLocatingGps: Boolean,
    onDismiss: () -> Unit,
    onSelectArea: (CampusArea) -> Unit,
    onGpsDetectClick: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Select Campus & Dining Area",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "Nearby restaurants, student partner discounts, and budget meal catalogs will sync automatically for your selected zone.",
                    fontSize = 12.sp,
                    color = Color.White.copy(alpha = 0.75f)
                )

                // GPS Detect Button
                Button(
                    onClick = onGpsDetectClick,
                    colors = ButtonDefaults.buttonColors(containerColor = MintAccent),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("gps_detect_btn")
                ) {
                    if (isLocatingGps) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            color = MintAccentDark,
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Detecting via GPS...",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = MintAccentDark
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.MyLocation,
                            contentDescription = "GPS",
                            tint = MintAccentDark,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Detect my Location (GPS)",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = MintAccentDark
                        )
                    }
                }

                HorizontalDivider(color = Color.White.copy(alpha = 0.15f), modifier = Modifier.padding(vertical = 4.dp))

                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 300.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(areas) { area ->
                        val isSelected = area.id == currentAreaId
                        val bg = if (isSelected) MintAccent.copy(alpha = 0.25f) else Color.White.copy(alpha = 0.08f)
                        val borderCol = if (isSelected) MintAccent else Color.White.copy(alpha = 0.18f)

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .background(bg)
                                .border(1.dp, borderCol, RoundedCornerShape(14.dp))
                                .clickable { onSelectArea(area) }
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Restaurant,
                                contentDescription = null,
                                tint = if (isSelected) MintAccent else Color.White.copy(alpha = 0.7f),
                                modifier = Modifier.size(20.dp)
                            )

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = area.name,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                                Text(
                                    text = area.description,
                                    fontSize = 11.sp,
                                    color = Color.White.copy(alpha = 0.7f)
                                )
                                Text(
                                    text = "${area.defaultRestaurants.size} Partners · ${area.defaultMeals.size} Meals in catalog",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MintAccentGlow
                                )
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
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close", color = Color.White)
            }
        },
        containerColor = Color(0xFF131F38),
        shape = RoundedCornerShape(24.dp)
    )
}
