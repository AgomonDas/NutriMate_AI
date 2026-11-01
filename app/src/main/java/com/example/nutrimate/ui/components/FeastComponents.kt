package com.example.nutrimate.ui.components

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.nutrimate.data.feast.FeastBudgetState
import com.example.nutrimate.data.feast.FeastEngine
import com.example.nutrimate.data.feast.PlannedFeast
import com.example.nutrimate.data.feast.TradeoffProposal
import com.example.nutrimate.ui.theme.*

@Composable
fun FeastBucketCard(
    state: FeastBudgetState,
    canUndo: Boolean,
    onUndoClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    GlassBox(
        modifier = modifier
            .fillMaxWidth()
            .testTag("feast_bucket_card"),
        cornerRadius = 24.dp
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Header Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "🍗",
                        fontSize = 24.sp
                    )
                    Column {
                        Text(
                            text = "Feast Reserve Buckets",
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Text(
                            text = "Monthly Pool: ${FeastEngine.formatTaka(state.poolPaisa)}",
                            fontSize = 12.sp,
                            color = Color.White.copy(alpha = 0.72f)
                        )
                    }
                }

                if (canUndo) {
                    Button(
                        onClick = onUndoClick,
                        colors = ButtonDefaults.buttonColors(containerColor = OrangeAccent),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .height(34.dp)
                            .testTag("feast_undo_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Undo,
                            contentDescription = "Undo",
                            tint = Color.Black,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Undo",
                            color = Color.Black,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            // Metric Pillars
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                BudgetMetricBox(
                    label = "Base Daily (b0)",
                    value = FeastEngine.formatTaka(state.baseDailyBudgetPaisa),
                    sublabel = "Pool ÷ 30d",
                    modifier = Modifier.weight(1f)
                )
                BudgetMetricBox(
                    label = "Unallocated (U)",
                    value = FeastEngine.formatTaka(state.unallocatedPaisa),
                    sublabel = "Flexible reserve",
                    highlightColor = MintAccent,
                    modifier = Modifier.weight(1f)
                )
            }

            // Planned Feasts List
            Text(
                text = "Upcoming Planned Feasts (2x Base Daily):",
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color.White.copy(alpha = 0.9f)
            )

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                state.feasts.forEach { feast ->
                    FeastItemRow(feast = feast)
                }
            }

            // Ledger Invariant Badge
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (state.isLedgerValid) MintAccent.copy(alpha = 0.15f) else Color.Red.copy(alpha = 0.2f))
                    .border(
                        1.dp,
                        if (state.isLedgerValid) MintAccent.copy(alpha = 0.5f) else Color.Red,
                        RoundedCornerShape(12.dp)
                    )
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = if (state.isLedgerValid) Icons.Default.CheckCircle else Icons.Default.Warning,
                    contentDescription = "Ledger Status",
                    tint = if (state.isLedgerValid) MintAccent else Color.Red,
                    modifier = Modifier.size(18.dp)
                )
                Text(
                    text = if (state.isLedgerValid)
                        "Ledger verified: sum(meals) + sum(feasts) + U ≤ Pool"
                    else "Ledger invariant violated!",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    color = if (state.isLedgerValid) MintAccentGlow else Color(0xFFFF8A8A)
                )
            }
        }
    }
}

@Composable
fun BudgetMetricBox(
    label: String,
    value: String,
    sublabel: String,
    highlightColor: Color = Color.White,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(Color.White.copy(alpha = 0.1f))
            .border(1.dp, Color.White.copy(alpha = 0.18f), RoundedCornerShape(16.dp))
            .padding(12.dp)
    ) {
        Column {
            Text(label, fontSize = 11.sp, color = Color.White.copy(alpha = 0.7f))
            Text(value, fontSize = 17.sp, fontWeight = FontWeight.Bold, color = highlightColor, modifier = Modifier.padding(top = 2.dp))
            Text(sublabel, fontSize = 10.sp, color = Color.White.copy(alpha = 0.55f))
        }
    }
}

@Composable
fun FeastItemRow(feast: PlannedFeast) {
    val isCancelled = feast.isCancelled
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(
                if (isCancelled) Color.Black.copy(alpha = 0.35f) else TagYellow.copy(alpha = 0.15f)
            )
            .border(
                1.dp,
                if (isCancelled) Color.White.copy(alpha = 0.15f) else TagYellow.copy(alpha = 0.5f),
                RoundedCornerShape(14.dp)
            )
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(text = if (isCancelled) "❌" else "🎉", fontSize = 18.sp)
            Column {
                Text(
                    text = feast.title,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (isCancelled) Color.White.copy(alpha = 0.5f) else Color.White,
                    textDecoration = if (isCancelled) TextDecoration.LineThrough else TextDecoration.None
                )
                Text(
                    text = feast.dateLabel,
                    fontSize = 11.sp,
                    color = Color.White.copy(alpha = 0.6f)
                )
            }
        }

        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(10.dp))
                .background(if (isCancelled) Color.White.copy(alpha = 0.15f) else TagYellow)
                .padding(horizontal = 10.dp, vertical = 4.dp)
        ) {
            Text(
                text = if (isCancelled) "CANCELLED" else FeastEngine.formatTaka(feast.budgetPaisa),
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = if (isCancelled) Color.White.copy(alpha = 0.7f) else TagYellowText
            )
        }
    }
}

@Composable
fun TradeoffProposalDialog(
    proposal: TradeoffProposal,
    onAccept: () -> Unit,
    onDecline: () -> Unit,
    modifier: Modifier = Modifier
) {
    AlertDialog(
        onDismissRequest = onDecline,
        containerColor = Color(0xFF0F1A30),
        shape = RoundedCornerShape(26.dp),
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("⚖️", fontSize = 24.sp)
                Text(
                    text = "Feast Budget Trade-off",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = proposal.plainEnglishExplanation,
                    fontSize = 14.sp,
                    color = Color.White.copy(alpha = 0.9f),
                    lineHeight = 20.sp
                )

                // Comparison Box
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color.White.copy(alpha = 0.08f))
                        .border(1.dp, Color.White.copy(alpha = 0.18f), RoundedCornerShape(16.dp))
                        .padding(14.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Meal Item:", fontSize = 12.sp, color = Color.White.copy(alpha = 0.7f))
                            Text(proposal.requestedItemName, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Restaurant:", fontSize = 12.sp, color = Color.White.copy(alpha = 0.7f))
                            Text(proposal.requestedPlaceName, fontSize = 13.sp, color = Color.White)
                        }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Current Allowance:", fontSize = 12.sp, color = Color.White.copy(alpha = 0.7f))
                            Text(FeastEngine.formatTaka(proposal.currentAllowancePaisa), fontSize = 13.sp, color = Color.White)
                        }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("New Price:", fontSize = 12.sp, color = Color.White.copy(alpha = 0.7f))
                            Text(FeastEngine.formatTaka(proposal.requestedPricePaisa), fontSize = 13.sp, fontWeight = FontWeight.Bold, color = MintAccent)
                        }

                        if (proposal.shortfallPaisa > 0) {
                            HorizontalDivider(color = Color.White.copy(alpha = 0.2f), thickness = 1.dp, modifier = Modifier.padding(vertical = 2.dp))
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Shortfall:", fontSize = 12.sp, color = Color(0xFFFF8A8A))
                                Text(FeastEngine.formatTaka(proposal.shortfallPaisa), fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color(0xFFFF8A8A))
                            }
                            if (proposal.feastToCancel != null) {
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text("Feast Sacrificed:", fontSize = 12.sp, color = TagYellow)
                                    Text(proposal.feastToCancel.title, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = TagYellow)
                                }
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text("Remainder to Unallocated:", fontSize = 12.sp, color = MintAccentGlow)
                                    Text("+${FeastEngine.formatTaka(proposal.remainderToUnallocatedPaisa)}", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = MintAccentGlow)
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onAccept,
                colors = ButtonDefaults.buttonColors(containerColor = MintAccent),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.testTag("tradeoff_accept_btn")
            ) {
                Text("Accept & Rearrange", color = MintAccentDark, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDecline,
                modifier = Modifier.testTag("tradeoff_decline_btn")
            ) {
                Text("Decline (Keep Feast)", color = Color.White.copy(alpha = 0.8f))
            }
        }
    )
}

@Composable
fun ManualMealEntryDialog(
    onDismiss: () -> Unit,
    onSubmit: (restaurant: String, dish: String, spentTaka: Int, kcal: Int, slotId: String) -> Unit
) {
    var restaurant by remember { mutableStateOf("") }
    var dish by remember { mutableStateOf("") }
    var spentStr by remember { mutableStateOf("") }
    var kcalStr by remember { mutableStateOf("") }
    var selectedSlot by remember { mutableStateOf("lunch") }

    val slots = listOf("breakfast" to "Breakfast 🍳", "lunch" to "Lunch 🍛", "snacks" to "Snacks 🥪", "dinner" to "Dinner 🍲")

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF0F1A30),
        shape = RoundedCornerShape(26.dp),
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("✍️", fontSize = 24.sp)
                Text("Log Custom Meal & Expense", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color.White)
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    "Updates your personal table and submits to community staging for central catalog curation.",
                    fontSize = 12.sp,
                    color = Color.White.copy(alpha = 0.72f)
                )

                OutlinedTextField(
                    value = restaurant,
                    onValueChange = { restaurant = it },
                    label = { Text("Restaurant / Stall Name", color = Color.White.copy(alpha = 0.7f)) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = MintAccent,
                        unfocusedBorderColor = Color.White.copy(alpha = 0.3f),
                        focusedContainerColor = GlassInputBg,
                        unfocusedContainerColor = GlassInputBg
                    ),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth().testTag("manual_restaurant_input")
                )

                OutlinedTextField(
                    value = dish,
                    onValueChange = { dish = it },
                    label = { Text("Dish / Meal Name", color = Color.White.copy(alpha = 0.7f)) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = MintAccent,
                        unfocusedBorderColor = Color.White.copy(alpha = 0.3f),
                        focusedContainerColor = GlassInputBg,
                        unfocusedContainerColor = GlassInputBg
                    ),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth().testTag("manual_dish_input")
                )

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = spentStr,
                        onValueChange = { spentStr = it },
                        label = { Text("Spent (৳)", color = Color.White.copy(alpha = 0.7f)) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = MintAccent,
                            unfocusedBorderColor = Color.White.copy(alpha = 0.3f),
                            focusedContainerColor = GlassInputBg,
                            unfocusedContainerColor = GlassInputBg
                        ),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.weight(1f).testTag("manual_spent_input")
                    )

                    OutlinedTextField(
                        value = kcalStr,
                        onValueChange = { kcalStr = it },
                        label = { Text("Calories", color = Color.White.copy(alpha = 0.7f)) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = MintAccent,
                            unfocusedBorderColor = Color.White.copy(alpha = 0.3f),
                            focusedContainerColor = GlassInputBg,
                            unfocusedContainerColor = GlassInputBg
                        ),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.weight(1f).testTag("manual_kcal_input")
                    )
                }

                // Slot selector
                Text("Select Meal Slot:", fontSize = 12.sp, color = Color.White.copy(alpha = 0.7f))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    slots.forEach { (slotId, label) ->
                        val isSelected = selectedSlot == slotId
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (isSelected) MintAccent else Color.White.copy(alpha = 0.12f))
                                .clickable { selectedSlot = slotId }
                                .padding(vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = label.split(" ")[0],
                                fontSize = 11.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                color = if (isSelected) MintAccentDark else Color.White
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val spent = spentStr.toIntOrNull() ?: 0
                    val kcal = kcalStr.toIntOrNull() ?: 500
                    if (restaurant.isNotBlank() && dish.isNotBlank() && spent > 0) {
                        onSubmit(restaurant, dish, spent, kcal, selectedSlot)
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = MintAccent),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.testTag("submit_manual_meal_btn")
            ) {
                Text("Save & Log", color = MintAccentDark, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = Color.White.copy(alpha = 0.7f))
            }
        }
    )
}
