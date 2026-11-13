package com.example.nutrimate.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.nutrimate.data.location.CampusArea
import com.example.nutrimate.data.location.CampusLocationManager
import com.example.nutrimate.data.location.MapDirectionsHelper
import com.example.nutrimate.ui.components.GlassBox
import com.example.nutrimate.ui.theme.*
import com.example.nutrimate.viewmodel.DashboardUiState
import com.example.nutrimate.viewmodel.DashboardViewModel
import com.example.nutrimate.viewmodel.DiscountedMealDeal
import com.example.nutrimate.viewmodel.WeeklyBudgetStatus
import java.util.Calendar

@Composable
fun DashboardScreen(
    viewModel: DashboardViewModel,
    userName: String,
    monthlyBudgetTk: Int,
    onOpenChat: () -> Unit,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var pendingDirectionsDeal by remember { mutableStateOf<DiscountedMealDeal?>(null) }

    // Location Permission Launcher
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val granted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (granted) {
            viewModel.detectLocation(context)
        } else {
            viewModel.setLocationPermissionDenied()
        }
    }

    // Auto-detect on first launch if permission is already granted
    LaunchedEffect(Unit) {
        val hasFine = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val hasCoarse = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        if (hasFine || hasCoarse) {
            viewModel.detectLocation(context)
        }
    }

    // Calculate edge-to-edge window insets padding
    val topInset = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val bottomInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 130.dp

    Box(modifier = modifier.fillMaxSize()) {
        // Interactive Scrollable Dashboard on top of global edge-to-edge blurred aura backdrop
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                top = topInset + 12.dp,
                bottom = bottomInset,
                start = 18.dp,
                end = 18.dp
            ),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // ORDER 1: Header Row
            item(key = "dashboard_header_row") {
                DashboardHeaderRow(
                    campusName = uiState.currentCampus.shortName,
                    isLocating = uiState.isLocatingGps,
                    onCampusClick = { viewModel.openCampusPicker() },
                    onGpsClick = {
                        val hasFine = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
                        val hasCoarse = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
                        if (hasFine || hasCoarse) {
                            viewModel.detectLocation(context)
                        } else {
                            permissionLauncher.launch(
                                arrayOf(
                                    Manifest.permission.ACCESS_FINE_LOCATION,
                                    Manifest.permission.ACCESS_COARSE_LOCATION
                                )
                            )
                        }
                    },
                    onOpenChat = onOpenChat
                )
            }

            // ORDER 2: Clean Welcome Message (no weather details)
            item(key = "welcome_hero_section") {
                WelcomeHeroSection(
                    name = userName.ifBlank { "Student" },
                    campusShort = uiState.currentCampus.shortName
                )
            }

            // ORDER 3: Optional Location Note Banner
            if (uiState.locationNote != null) {
                item(key = "location_note_banner") {
                    GlassBox(
                        modifier = Modifier.fillMaxWidth(),
                        cornerRadius = 16.dp,
                        backgroundColor = Color.White.copy(alpha = 0.12f)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = "Info",
                                tint = MintAccent,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = uiState.locationNote ?: "",
                                fontSize = 12.sp,
                                color = Color.White.copy(alpha = 0.9f),
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }

            // ORDER 4: Weekly Meal Budget Card
            item(key = "weekly_budget_card") {
                WeeklyBudgetCard(uiState = uiState)
            }

            // ORDER 5: Deals Header & Filters
            item(key = "deals_header_filters") {
                DealsFilterSection(
                    campusShort = uiState.currentCampus.shortName,
                    dealCount = uiState.filteredDeals.size,
                    selectedSlot = uiState.selectedSlotFilter,
                    isVegOnly = uiState.isVegOnly,
                    isDealsOnly = uiState.isDealsOnly,
                    sortOption = uiState.sortOption,
                    onSlotSelected = { viewModel.setSlotFilter(it) },
                    onToggleVeg = { viewModel.toggleVegOnly() },
                    onToggleDealsOnly = { viewModel.toggleDealsOnly() },
                    onCycleSort = { viewModel.cycleSortOption() }
                )
            }

            // ORDER 6: Deal Cards / States
            when {
                uiState.isDealsLoading -> {
                    item(key = "deals_loading") {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 32.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(
                                color = MintAccent,
                                strokeWidth = 3.dp,
                                modifier = Modifier.size(36.dp)
                            )
                        }
                    }
                }

                uiState.dealsError != null -> {
                    item(key = "deals_error") {
                        GlassBox(modifier = Modifier.fillMaxWidth(), cornerRadius = 18.dp) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(20.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = "Couldn't load nearby deals",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                                Text(
                                    text = uiState.dealsError ?: "Please check connection",
                                    fontSize = 12.sp,
                                    color = Color.White.copy(alpha = 0.7f)
                                )
                                Button(
                                    onClick = { viewModel.loadDealsForCampus(uiState.currentCampus) },
                                    colors = ButtonDefaults.buttonColors(containerColor = MintAccent),
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.padding(top = 4.dp)
                                ) {
                                    Text("Retry", color = MintAccentDark, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }

                uiState.rawDeals.isEmpty() -> {
                    item(key = "deals_empty_campus") {
                        GlassBox(modifier = Modifier.fillMaxWidth(), cornerRadius = 18.dp) {
                            Text(
                                text = "No student deals found near ${uiState.currentCampus.shortName} yet. Tap 'KUET' or another campus above to explore meals!",
                                fontSize = 13.sp,
                                color = Color.White.copy(alpha = 0.85f),
                                modifier = Modifier.padding(18.dp)
                            )
                        }
                    }
                }

                uiState.filteredDeals.isEmpty() -> {
                    item(key = "deals_empty_filters") {
                        GlassBox(modifier = Modifier.fillMaxWidth(), cornerRadius = 18.dp) {
                            Column(
                                modifier = Modifier.padding(18.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(
                                    text = "No meals match the current filter",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                                Text(
                                    text = "Try switching slot tabs or turning off active filters.",
                                    fontSize = 12.sp,
                                    color = Color.White.copy(alpha = 0.7f)
                                )
                            }
                        }
                    }
                }

                else -> {
                    items(uiState.filteredDeals, key = { it.key }) { deal ->
                        DealCard(
                            deal = deal,
                            onLongClick = { pendingDirectionsDeal = deal }
                        )
                    }
                }
            }
        }

        // Campus Picker Dialog (15 Campuses)
        if (uiState.showCampusPicker) {
            CampusPickerDialog(
                currentCampus = uiState.currentCampus,
                campuses = CampusLocationManager.CAMPUS_AREAS,
                onSelectCampus = { campus -> viewModel.selectCampus(campus) },
                onDismiss = { viewModel.dismissCampusPicker() }
            )
        }

        // Long-press Google Maps Directions Confirmation Dialog
        pendingDirectionsDeal?.let { deal ->
            val resolvedCoords = remember(deal) {
                MapDirectionsHelper.resolveCoordinates(
                    placeName = deal.restaurantName,
                    storedLat = deal.latitude,
                    storedLon = deal.longitude,
                    fallbackArea = uiState.currentCampus
                )
            }
            val displayAddress = deal.address.ifBlank { resolvedCoords.address }

            AlertDialog(
                onDismissRequest = { pendingDirectionsDeal = null },
                containerColor = Color(0xFF0F1A30),
                shape = RoundedCornerShape(22.dp),
                modifier = Modifier.testTag("directions_confirm_dialog"),
                icon = {
                    Icon(
                        imageVector = Icons.Default.Directions,
                        contentDescription = "Directions",
                        tint = MintAccent,
                        modifier = Modifier.size(28.dp)
                    )
                },
                title = {
                    Text(
                        text = "Get Directions?",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = "Do you want to open Google Maps and see directions to ${deal.restaurantName} for ${deal.mealName}?",
                            fontSize = 14.sp,
                            color = Color.White.copy(alpha = 0.9f)
                        )
                        if (displayAddress.isNotBlank()) {
                            Text(
                                text = "📍 $displayAddress",
                                fontSize = 12.sp,
                                color = MintAccentGlow
                            )
                        }
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            pendingDirectionsDeal = null
                            MapDirectionsHelper.openGoogleMapsDirections(
                                context = context,
                                restaurantName = deal.restaurantName,
                                latitude = resolvedCoords.latitude,
                                longitude = resolvedCoords.longitude
                            )
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MintAccent),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.testTag("confirm_directions_btn")
                    ) {
                        Text(
                            text = "Show Directions",
                            color = MintAccentDark,
                            fontWeight = FontWeight.Bold
                        )
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = { pendingDirectionsDeal = null },
                        modifier = Modifier.testTag("cancel_directions_btn")
                    ) {
                        Text("Cancel", color = Color.White.copy(alpha = 0.75f))
                    }
                }
            )
        }
    }
}

// ------------------------------------------------------------------------------------------------
// SUBCOMPONENTS
// ------------------------------------------------------------------------------------------------

@Composable
private fun DashboardHeaderRow(
    campusName: String,
    isLocating: Boolean,
    onCampusClick: () -> Unit,
    onGpsClick: () -> Unit,
    onOpenChat: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        // Campus Selector Pill ("KUET, Khulna" with location icon & dropdown arrow)
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(20.dp))
                .background(Color.Black.copy(alpha = 0.35f))
                .border(1.dp, Color.White.copy(alpha = 0.28f), RoundedCornerShape(20.dp))
                .clickable(onClick = onCampusClick)
                .padding(horizontal = 14.dp, vertical = 8.dp)
                .testTag("campus_selector_pill")
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.LocationOn,
                    contentDescription = "Location",
                    tint = MintAccent,
                    modifier = Modifier.size(17.dp)
                )
                Text(
                    text = campusName,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Icon(
                    imageVector = Icons.Default.KeyboardArrowDown,
                    contentDescription = "Select",
                    tint = Color.White.copy(alpha = 0.8f),
                    modifier = Modifier.size(16.dp)
                )
            }
        }

        // Action Buttons: "Use my location" and "Ask AI"
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            // Location GPS Button
            IconButton(
                onClick = onGpsClick,
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.35f))
                    .border(1.dp, Color.White.copy(alpha = 0.28f), CircleShape)
                    .testTag("use_my_location_btn")
            ) {
                if (isLocating) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        color = MintAccent,
                        strokeWidth = 2.dp
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.MyLocation,
                        contentDescription = "Use my location",
                        tint = Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            // Ask AI Button
            IconButton(
                onClick = onOpenChat,
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.35f))
                    .border(1.dp, Color.White.copy(alpha = 0.28f), CircleShape)
                    .testTag("ask_ai_btn")
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Chat,
                    contentDescription = "Ask AI",
                    tint = MintAccentGlow,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

@Composable
private fun WelcomeHeroSection(
    name: String,
    campusShort: String
) {
    val currentHour = remember { Calendar.getInstance().get(Calendar.HOUR_OF_DAY) }
    val greeting = remember(currentHour) {
        when {
            currentHour < 12 -> "Good morning"
            currentHour < 17 -> "Good afternoon"
            else -> "Good evening"
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .testTag("welcome_hero_section"),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            text = "NUTRIMATE · $campusShort".uppercase(),
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = MintAccentGlow.copy(alpha = 0.9f),
            letterSpacing = 1.1.sp
        )

        Text(
            text = "$greeting, $name",
            fontSize = 30.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            lineHeight = 36.sp
        )

        Text(
            text = "Student deals, nearby meals & your weekly food budget.",
            fontSize = 13.sp,
            color = Color.White.copy(alpha = 0.76f)
        )
    }
}

@Composable
private fun WeeklyBudgetCard(uiState: DashboardUiState) {
    val weeklyBudget = uiState.weeklyBudgetTk
    val spent = uiState.weeklySpentTk
    val remaining = weeklyBudget - spent
    val status = uiState.budgetStatus

    val statusColor = when (status) {
        WeeklyBudgetStatus.ON_TRACK -> MintAccent
        WeeklyBudgetStatus.SPENDING_FAST -> Color(0xFFFFA726)
        WeeklyBudgetStatus.OVER_BUDGET -> Color(0xFFFF5252)
        WeeklyBudgetStatus.NO_BUDGET -> Color.LightGray
    }

    val animatedProgress by animateFloatAsState(
        targetValue = uiState.progressFraction.coerceIn(0f, 1f),
        animationSpec = tween(durationMillis = 800),
        label = "budget_progress"
    )

    GlassBox(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("weekly_budget_card"),
        cornerRadius = 24.dp
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Header: Title & Status Chip
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "THIS WEEK'S MEAL BUDGET",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White.copy(alpha = 0.78f),
                    letterSpacing = 0.8.sp
                )

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(statusColor.copy(alpha = 0.22f))
                        .border(1.dp, statusColor.copy(alpha = 0.7f), RoundedCornerShape(8.dp))
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                ) {
                    Text(
                        text = status.label,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = statusColor
                    )
                }
            }

            if (weeklyBudget > 0) {
                // Big Spent vs Budget
                Row(
                    verticalAlignment = Alignment.Bottom,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = "৳%,d".format(spent),
                        fontSize = 28.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color.White
                    )
                    Text(
                        text = "of ৳%,d".format(weeklyBudget),
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color.White.copy(alpha = 0.75f),
                        modifier = Modifier.padding(bottom = 3.dp)
                    )
                }

                // 14dp Progress Bar with White Tick at elapsedFraction
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(14.dp)
                        .clip(RoundedCornerShape(7.dp))
                        .background(Color.White.copy(alpha = 0.16f))
                ) {
                    // Filled progress
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .fillMaxWidth(animatedProgress)
                            .clip(RoundedCornerShape(7.dp))
                            .background(statusColor)
                    )

                    // White Tick Marker at week.elapsedFraction
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .fillMaxWidth(uiState.weekElapsedFraction),
                        contentAlignment = Alignment.CenterEnd
                    ) {
                        Box(
                            modifier = Modifier
                                .width(3.dp)
                                .fillMaxHeight()
                                .background(Color.White)
                        )
                    }
                }

                // Footer calculation
                val footerText = if (remaining >= 0) {
                    val perDay = remaining / uiState.daysRemainingInWeek
                    "৳%,d left · about ৳%,d/day for %d more days".format(remaining, perDay, uiState.daysRemainingInWeek)
                } else {
                    "৳%,d over this week's budget".format(-remaining)
                }

                Text(
                    text = footerText,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White
                )

                Text(
                    text = "White marker = where you'd be if you spent evenly.",
                    fontSize = 11.sp,
                    color = Color.White.copy(alpha = 0.65f)
                )
            } else {
                Text(
                    text = "Set your monthly budget in Profile to track this week.",
                    fontSize = 13.sp,
                    color = Color.White.copy(alpha = 0.85f),
                    modifier = Modifier.padding(vertical = 4.dp)
                )
            }
        }
    }
}

@Composable
private fun DealsFilterSection(
    campusShort: String,
    dealCount: Int,
    selectedSlot: String,
    isVegOnly: Boolean,
    isDealsOnly: Boolean,
    sortOption: com.example.nutrimate.viewmodel.DealSortOption,
    onSlotSelected: (String) -> Unit,
    onToggleVeg: () -> Unit,
    onToggleDealsOnly: () -> Unit,
    onCycleSort: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Deals & meals title with count
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "Deals & meals near $campusShort",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )

            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color.White.copy(alpha = 0.15f))
                    .padding(horizontal = 8.dp, vertical = 2.dp)
            ) {
                Text(
                    text = "$dealCount options",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White.copy(alpha = 0.85f)
                )
            }
        }

        // First Chip Row: All, Breakfast, Lunch, Snacks, Dinner
        val slots = listOf("All", "Breakfast", "Lunch", "Snacks", "Dinner")
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            slots.forEach { slot ->
                val isSelected = selectedSlot.equals(slot, ignoreCase = true)
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(16.dp))
                        .background(if (isSelected) MintAccent else Color.White.copy(alpha = 0.14f))
                        .border(
                            1.dp,
                            if (isSelected) MintAccent else Color.White.copy(alpha = 0.25f),
                            RoundedCornerShape(16.dp)
                        )
                        .clickable { onSlotSelected(slot) }
                        .padding(horizontal = 14.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = slot,
                        fontSize = 12.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                        color = if (isSelected) MintAccentDark else Color.White
                    )
                }
            }
        }

        // Second Chip Row: "🏷️ Deals only", "🌱 Veg only", and "Sort: …"
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Deals Only Toggle Chip
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(16.dp))
                    .background(if (isDealsOnly) MintAccent.copy(alpha = 0.28f) else Color.White.copy(alpha = 0.14f))
                    .border(
                        1.dp,
                        if (isDealsOnly) MintAccent else Color.White.copy(alpha = 0.25f),
                        RoundedCornerShape(16.dp)
                    )
                    .clickable(onClick = onToggleDealsOnly)
                    .padding(horizontal = 12.dp, vertical = 6.dp)
                    .testTag("filter_deals_toggle")
            ) {
                Text(
                    text = "🏷️ Deals only",
                    fontSize = 12.sp,
                    fontWeight = if (isDealsOnly) FontWeight.Bold else FontWeight.Normal,
                    color = if (isDealsOnly) MintAccentGlow else Color.White
                )
            }

            // Veg Only Toggle Chip
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(16.dp))
                    .background(if (isVegOnly) Color(0xFF2E7D32) else Color.White.copy(alpha = 0.14f))
                    .border(
                        1.dp,
                        if (isVegOnly) Color(0xFF81C784) else Color.White.copy(alpha = 0.25f),
                        RoundedCornerShape(16.dp)
                    )
                    .clickable(onClick = onToggleVeg)
                    .padding(horizontal = 12.dp, vertical = 6.dp)
                    .testTag("filter_veg_toggle")
            ) {
                Text(
                    text = "🌱 Veg only",
                    fontSize = 12.sp,
                    fontWeight = if (isVegOnly) FontWeight.Bold else FontWeight.Normal,
                    color = Color.White
                )
            }

            // Cycling Sort Chip
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color.White.copy(alpha = 0.14f))
                    .border(1.dp, MintAccent.copy(alpha = 0.5f), RoundedCornerShape(16.dp))
                    .clickable(onClick = onCycleSort)
                    .padding(horizontal = 12.dp, vertical = 6.dp)
                    .testTag("filter_sort_cycle")
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Sort,
                        contentDescription = "Sort",
                        tint = MintAccent,
                        modifier = Modifier.size(14.dp)
                    )
                    Text(
                        text = sortOption.label,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color.White
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DealCard(
    deal: DiscountedMealDeal,
    onLongClick: () -> Unit = {}
) {
    val haptics = LocalHapticFeedback.current
    val slotEmoji = when (deal.slot.lowercase()) {
        "breakfast" -> "🍳"
        "lunch" -> "🍛"
        "snacks", "snack" -> "🥪"
        "dinner" -> "🍲"
        else -> "🍽️"
    }

    GlassBox(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("deal_card_${deal.key}"),
        cornerRadius = 18.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .combinedClickable(
                    onClick = {},
                    onLongClick = {
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        onLongClick()
                    }
                )
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Emoji slot badge tile
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color.White.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center
            ) {
                Text(text = slotEmoji, fontSize = 24.sp)
            }

            // Center details: Name, Restaurant & Meta, Tags
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                Text(
                    text = deal.mealName,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                Text(
                    text = "${deal.restaurantName} · ${deal.distanceFormatted} · ⭐ ${deal.rating}",
                    fontSize = 11.sp,
                    color = Color.White.copy(alpha = 0.72f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                // Tags row (kcal, Veg, Student partner)
                Row(
                    modifier = Modifier.padding(top = 2.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Kcal Tag
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color.White.copy(alpha = 0.12f))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = "${deal.kcal} kcal",
                            fontSize = 10.sp,
                            color = Color.White.copy(alpha = 0.85f)
                        )
                    }

                    if (deal.isVegetarian) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(Color(0xFF2E7D32).copy(alpha = 0.35f))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = "Veg",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF81C784)
                            )
                        }
                    }

                    if (deal.isPartner) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(TagYellow.copy(alpha = 0.25f))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = "Partner",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = TagYellowText
                            )
                        }
                    }
                }
            }

            // Right side: Discount badge (when partner deal), Bold price & Strikethrough Original
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                if (deal.discountPercent > 0 && deal.discountTag.isNotBlank()) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(MintAccent.copy(alpha = 0.25f))
                            .border(1.dp, MintAccent.copy(alpha = 0.6f), RoundedCornerShape(8.dp))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = deal.discountTag,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = MintAccent
                        )
                    }
                } else {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color.White.copy(alpha = 0.12f))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = deal.slot.replaceFirstChar { it.uppercase() },
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Medium,
                            color = Color.White.copy(alpha = 0.75f)
                        )
                    }
                }

                // Final price
                Text(
                    text = "৳%,d".format(deal.discountedPriceTk),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color.White
                )

                // Strikethrough Original price only when discounted
                if (deal.discountPercent > 0 && deal.originalPriceTk > deal.discountedPriceTk) {
                    Text(
                        text = "৳%,d".format(deal.originalPriceTk),
                        fontSize = 12.sp,
                        color = Color.White.copy(alpha = 0.55f),
                        textDecoration = TextDecoration.LineThrough
                    )
                }
            }
        }
    }
}

@Composable
private fun CampusPickerDialog(
    currentCampus: CampusArea,
    campuses: List<CampusArea>,
    onSelectCampus: (CampusArea) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF0F1A30),
        title = {
            Text(
                text = "Select Campus Hub",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
        },
        text = {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 420.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(campuses, key = { it.id }) { campus ->
                    val isSelected = campus.id == currentCampus.id
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (isSelected) MintAccent.copy(alpha = 0.22f) else Color.White.copy(alpha = 0.08f))
                            .border(
                                1.dp,
                                if (isSelected) MintAccent else Color.White.copy(alpha = 0.15f),
                                RoundedCornerShape(12.dp)
                            )
                            .clickable { onSelectCampus(campus) }
                            .padding(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = campus.name,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isSelected) MintAccent else Color.White
                                )
                                Text(
                                    text = "${campus.district} · ${campus.description}",
                                    fontSize = 11.sp,
                                    color = Color.White.copy(alpha = 0.65f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }

                            if (isSelected) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = "Selected",
                                    tint = MintAccent,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close", color = MintAccent, fontWeight = FontWeight.Bold)
            }
        }
    )
}
