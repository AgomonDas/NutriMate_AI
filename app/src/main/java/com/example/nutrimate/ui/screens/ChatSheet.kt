package com.example.nutrimate.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.nutrimate.model.ChatMessage
import com.example.nutrimate.model.MessageSender
import com.example.nutrimate.ui.components.GlassBox
import com.example.nutrimate.ui.theme.*
import com.example.nutrimate.viewmodel.NutriMateViewModel

@Composable
fun ChatSheet(
    viewModel: NutriMateViewModel,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    var inputText by remember { mutableStateOf("") }
    val listState = rememberLazyListState()
    val isGenerating = uiState.isAiThinking || uiState.isStreamingResponse
    val lastContentLen = uiState.chatMessages.lastOrNull()?.content?.length ?: 0

    LaunchedEffect(uiState.chatMessages.size, uiState.isAiThinking, lastContentLen) {
        if (uiState.chatMessages.isNotEmpty() || (uiState.isAiThinking && !uiState.isStreamingResponse)) {
            val extra = if (uiState.isAiThinking && !uiState.isStreamingResponse) 0 else -1
            val targetIdx = (uiState.chatMessages.size + extra).coerceAtLeast(0)
            listState.scrollToItem(targetIdx)
        }
    }

    // Scroll automatically when the software keyboard opens
    val imeBottom = WindowInsets.ime.getBottom(androidx.compose.ui.platform.LocalDensity.current)
    LaunchedEffect(imeBottom) {
        if (imeBottom > 0 && uiState.chatMessages.isNotEmpty()) {
            listState.animateScrollToItem((uiState.chatMessages.size - 1).coerceAtLeast(0))
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xE60A1428))
            .clickable(onClick = onDismiss)
    ) {
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .fillMaxHeight(0.92f)
                .imePadding()
                .navigationBarsPadding()
                .clip(RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp))
                .background(Color(0xFF0F1A30))
                .border(
                    width = 1.dp,
                    brush = Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.35f), Color.Transparent)),
                    shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp)
                )
                .clickable(enabled = false) {} // prevent dismissing when tapping inside
                .padding(horizontal = 20.dp, vertical = 14.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "NutriMate AI",
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(MintAccent.copy(alpha = 0.2f))
                                .border(1.dp, MintAccent.copy(alpha = 0.6f), RoundedCornerShape(8.dp))
                                .padding(horizontal = 7.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = "⚡ Hybrid Copilot",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = MintAccentGlow
                            )
                        }
                    }
                    Text(
                        text = "Personalized nutrition & ৳ budget · NutriMate Copilot",
                        fontSize = 12.sp,
                        color = Color.White.copy(alpha = 0.72f),
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (uiState.chatMessages.isNotEmpty()) {
                        IconButton(
                            onClick = { viewModel.clearChatHistory() },
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(Color.White.copy(alpha = 0.12f))
                                .testTag("clear_chat_btn")
                        ) {
                            Icon(
                                imageVector = Icons.Default.DeleteOutline,
                                contentDescription = "New Chat",
                                tint = Color.White.copy(alpha = 0.85f),
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }

                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.15f))
                            .testTag("close_chat_btn")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close Chat",
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Messages List or Empty State
            if (uiState.chatMessages.isEmpty() && !uiState.isAiThinking) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    GlassBox(
                        modifier = Modifier.fillMaxWidth(),
                        cornerRadius = 22.dp,
                        backgroundColor = Color.White.copy(alpha = 0.10f),
                        borderColor = Color.White.copy(alpha = 0.25f)
                    ) {
                        Column(
                            modifier = Modifier.padding(20.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text(text = "🥗", fontSize = 36.sp)
                            Text(
                                text = "Ask NutriMate AI",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                            Text(
                                text = "Goal: ${uiState.userGoals.dailyCalorieGoal} kcal/day · Budget: ৳${uiState.userGoals.monthlyBudget}/mo. Ask for Bangladeshi high-protein meals, calorie tips, or tap a shortcut below.",
                                fontSize = 13.sp,
                                color = Color.White.copy(alpha = 0.78f),
                                lineHeight = 18.sp
                            )
                        }
                    }
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(vertical = 8.dp)
                ) {
                    items(uiState.chatMessages, key = { it.id }) { msg ->
                        ChatBubble(
                            message = msg,
                            onConfirmTradeoff = { token -> viewModel.confirmInChatTradeoff(token) },
                            onDeclineTradeoff = { token -> viewModel.declineInChatTradeoff(token) },
                            onRetry = { msgId -> viewModel.retryChatMessage(msgId) }
                        )
                    }

                    if (uiState.isAiThinking && !uiState.isStreamingResponse) {
                        item(key = "ai_thinking_indicator") {
                            AiThinkingBubble()
                        }
                    }
                }
            }

            // Quick suggestion chips
            val quickChips = listOf(
                "🍛 What should I eat for dinner?",
                "💪 High-protein meals under ৳80",
                "🥪 Swap snack to fruit cart",
                "💸 Cheaper dinner",
                "🍛 I want biriyani for lunch"
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                quickChips.forEach { chipText ->
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(16.dp))
                            .background(Color.White.copy(alpha = if (isGenerating) 0.06f else 0.12f))
                            .border(1.dp, Color.White.copy(alpha = 0.25f), RoundedCornerShape(16.dp))
                            .clickable(enabled = !isGenerating) {
                                viewModel.sendChatMessage(chipText)
                            }
                            .padding(horizontal = 12.dp, vertical = 7.dp)
                    ) {
                        Text(
                            text = chipText,
                            fontSize = 12.sp,
                            color = if (isGenerating) Color.White.copy(alpha = 0.45f) else Color.White,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }

            // Input Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = inputText,
                    onValueChange = { inputText = it },
                    placeholder = {
                        Text(
                            if (isGenerating) "NutriMate is responding…" else "Ask nutrition, budget, or swap a meal…",
                            color = Color.White.copy(alpha = 0.5f),
                            fontSize = 14.sp
                        )
                    },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(
                        onSend = {
                            if (!isGenerating && inputText.isNotBlank()) {
                                viewModel.sendChatMessage(inputText)
                                inputText = ""
                            }
                        }
                    ),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = MintAccent,
                        unfocusedBorderColor = Color.White.copy(alpha = 0.25f),
                        focusedContainerColor = GlassInputBg,
                        unfocusedContainerColor = GlassInputBg
                    ),
                    shape = RoundedCornerShape(24.dp),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("chat_input_field")
                )

                Box(
                    modifier = Modifier
                        .size(50.dp)
                        .clip(CircleShape)
                        .background(
                            if (isGenerating) {
                                Brush.verticalGradient(listOf(Color(0xFFFF8A80), Color(0xFFE53935)))
                            } else {
                                Brush.verticalGradient(listOf(Color(0xFFB9FFD7), Color(0xFF6AF0A6)))
                            }
                        )
                        .clickable {
                            if (isGenerating) {
                                viewModel.cancelChatGeneration()
                            } else if (inputText.isNotBlank()) {
                                viewModel.sendChatMessage(inputText)
                                inputText = ""
                            }
                        }
                        .testTag(if (isGenerating) "chat_stop_btn" else "chat_send_btn"),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isGenerating) Icons.Default.Stop else Icons.AutoMirrored.Filled.Send,
                        contentDescription = if (isGenerating) "Stop response" else "Send",
                        tint = if (isGenerating) Color.White else MintAccentDark,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun ChatBubble(
    message: ChatMessage,
    onConfirmTradeoff: (String) -> Unit = {},
    onDeclineTradeoff: (String) -> Unit = {},
    onRetry: (String) -> Unit = {}
) {
    when (message.sender) {
        MessageSender.USER -> {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                Box(
                    modifier = Modifier
                        .widthIn(max = 280.dp)
                        .clip(RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp, bottomStart = 18.dp, bottomEnd = 4.dp))
                        .background(
                            Brush.verticalGradient(listOf(Color(0xFFB9FFD7), Color(0xFF6AF0A6)))
                        )
                        .padding(horizontal = 14.dp, vertical = 10.dp)
                ) {
                    Text(
                        text = message.content,
                        color = MintAccentDark,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        lineHeight = 20.sp
                    )
                }
            }
        }

        MessageSender.ASSISTANT -> {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Start
            ) {
                Column(modifier = Modifier.widthIn(max = 320.dp)) {
                    GlassBox(
                        modifier = Modifier.fillMaxWidth(),
                        cornerRadius = 18.dp,
                        backgroundColor = if (message.isError) Color(0x33FF6B6B) else Color.White.copy(alpha = 0.14f),
                        borderColor = when {
                            message.isError -> Color(0x99FF8A80)
                            message.isStreaming -> MintAccent.copy(alpha = 0.7f)
                            else -> Color.White.copy(alpha = 0.3f)
                        }
                    ) {
                        Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 11.dp)) {
                            Text(
                                text = message.content,
                                color = Color.White,
                                fontSize = 14.sp,
                                lineHeight = 20.sp
                            )

                            if (message.isError && message.retryPrompt != null) {
                                Spacer(modifier = Modifier.height(8.dp))
                                OutlinedButton(
                                    onClick = { onRetry(message.id) },
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                                    shape = RoundedCornerShape(12.dp),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                                    modifier = Modifier
                                        .height(36.dp)
                                        .testTag("retry_chat_btn")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Refresh,
                                        contentDescription = "Retry",
                                        modifier = Modifier.size(15.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Retry", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                }
                            } else if (message.isStreaming || message.latencyMs != null) {
                                Spacer(modifier = Modifier.height(6.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.End,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    val tagText = when {
                                        message.isStreaming -> {
                                            val ttft = message.firstTokenMs?.let { "TTFT ${it}ms · " } ?: ""
                                            "● ${ttft}streaming…"
                                        }
                                        message.isLocalExecution -> "⚡ ${message.latencyMs}ms · on-device"
                                        message.firstTokenMs != null ->
                                            "☁️ TTFT ${message.firstTokenMs}ms · ${message.latencyMs}ms · Template"
                                        else -> "☁️ ${message.latencyMs}ms · Template"
                                    }
                                    Text(
                                        text = tagText,
                                        fontSize = 10.sp,
                                        fontFamily = FontFamily.Monospace,
                                        color = if (message.isLocalExecution || message.isStreaming) MintAccentGlow else Color.White.copy(alpha = 0.6f)
                                    )
                                }
                            }
                        }
                    }

                    // Inline Trade-off Confirmation Action Box
                    if (message.isPendingConfirmation && message.tradeoffToken != null) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(16.dp))
                                .background(Color(0xFF14223A))
                                .border(1.dp, TagYellow.copy(alpha = 0.6f), RoundedCornerShape(16.dp))
                                .padding(12.dp)
                        ) {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(
                                    text = "⚠️ Feast Sacrifice Required",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = TagYellow
                                )
                                Text(
                                    text = "To fund this upgrade, an upcoming planned feast will be cancelled. Remainder returns to your savings.",
                                    fontSize = 11.sp,
                                    color = Color.White.copy(alpha = 0.8f),
                                    lineHeight = 15.sp
                                )
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Button(
                                        onClick = { onConfirmTradeoff(message.tradeoffToken) },
                                        colors = ButtonDefaults.buttonColors(containerColor = MintAccent),
                                        shape = RoundedCornerShape(12.dp),
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(38.dp)
                                            .testTag("inchat_confirm_btn"),
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                                    ) {
                                        Text(
                                            text = "Confirm",
                                            color = MintAccentDark,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 12.sp
                                        )
                                    }

                                    OutlinedButton(
                                        onClick = { onDeclineTradeoff(message.tradeoffToken) },
                                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                                        shape = RoundedCornerShape(12.dp),
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(38.dp)
                                            .testTag("inchat_decline_btn"),
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                                    ) {
                                        Text(
                                            text = "Decline",
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        MessageSender.FUNCTION_CALL -> {
            CollapsibleToolStepCard(message = message)
        }
    }
}

/**
 * Collapsible Monospace Step Card with Latency Tag.
 * Allows users to inspect deterministic on-device function calls and parameter payloads.
 */
@Composable
fun CollapsibleToolStepCard(
    message: ChatMessage,
    modifier: Modifier = Modifier
) {
    var isExpanded by remember { mutableStateOf(false) }

    val toolIcon = when (message.toolName) {
        "search_menu" -> "🔍"
        "check" -> "⚖️"
        "find_funding" -> "💡"
        "update" -> "🔒"
        "gatekeeper.confirm" -> "🔓"
        "get_alternatives" -> "🥗"
        else -> "⚙️"
    }

    val latencyBadge = when {
        message.latencyMs != null && message.isLocalExecution -> "⚡ ${message.latencyMs}ms · on-device"
        message.latencyMs != null && !message.isLocalExecution -> "☁️ ${message.latencyMs}ms · Template"
        message.isLocalExecution -> "⚡ local"
        else -> "☁️ cloud"
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Start
    ) {
        Box(
            modifier = modifier
                .widthIn(max = 340.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(Color(0xFF070E1A))
                .border(1.dp, MintAccent.copy(alpha = 0.5f), RoundedCornerShape(14.dp))
                .clickable { isExpanded = !isExpanded }
                .padding(horizontal = 12.dp, vertical = 8.dp)
                .testTag("tool_step_${message.toolName}")
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                // Header Row: Icon, Tool Name, Latency Tag, Expand Chevron
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(text = toolIcon, fontSize = 12.sp)
                        Text(
                            text = message.toolName ?: "tool_call",
                            color = MintAccent,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(
                                    if (message.isLocalExecution) MintAccent.copy(alpha = 0.18f)
                                    else Color(0xFF2C3E50).copy(alpha = 0.6f)
                                )
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = latencyBadge,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = if (message.isLocalExecution) MintAccentGlow else Color.White.copy(alpha = 0.85f),
                                fontFamily = FontFamily.Monospace
                            )
                        }

                        Icon(
                            imageVector = if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                            contentDescription = if (isExpanded) "Collapse" else "Expand",
                            tint = Color.White.copy(alpha = 0.7f),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }

                // Summary / Result preview
                if (message.toolResult != null) {
                    Text(
                        text = "↳ ${message.toolResult}",
                        color = Color.White.copy(alpha = 0.9f),
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        maxLines = if (isExpanded) Int.MAX_VALUE else 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                // Expanded Details (Args & full content)
                AnimatedVisibility(visible = isExpanded) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp),
                        verticalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        HorizontalDivider(
                            color = Color.White.copy(alpha = 0.12f),
                            thickness = 1.dp,
                            modifier = Modifier.padding(vertical = 4.dp)
                        )

                        if (message.toolArgs != null) {
                            Text(
                                text = "args: (${message.toolArgs})",
                                color = Color.White.copy(alpha = 0.75f),
                                fontSize = 10.sp,
                                fontFamily = FontFamily.Monospace
                            )
                        }

                        Text(
                            text = "content: ${message.content}",
                            color = MintAccentGlow.copy(alpha = 0.85f),
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            }
        }
    }
}

/**
 * Animated thinking indicator with pulsing dots shown while Gemini/router is processing.
 */
@Composable
fun AiThinkingBubble() {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val alpha1 by infiniteTransition.animateFloat(
        initialValue = 0.2f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(600, easing = LinearEasing), RepeatMode.Reverse),
        label = "dot1"
    )
    val alpha2 by infiniteTransition.animateFloat(
        initialValue = 0.2f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(600, delayMillis = 200, easing = LinearEasing), RepeatMode.Reverse),
        label = "dot2"
    )
    val alpha3 by infiniteTransition.animateFloat(
        initialValue = 0.2f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(600, delayMillis = 400, easing = LinearEasing), RepeatMode.Reverse),
        label = "dot3"
    )

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Start
    ) {
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(16.dp))
                .background(Color(0xFF070E1A))
                .border(1.dp, MintAccent.copy(alpha = 0.35f), RoundedCornerShape(16.dp))
                .padding(horizontal = 14.dp, vertical = 9.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "NutriMate reasoning",
                    fontSize = 11.sp,
                    color = Color.White.copy(alpha = 0.8f),
                    fontFamily = FontFamily.Monospace
                )
                Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                    Box(modifier = Modifier.size(6.dp).clip(CircleShape).background(MintAccent.copy(alpha = alpha1)))
                    Box(modifier = Modifier.size(6.dp).clip(CircleShape).background(MintAccent.copy(alpha = alpha2)))
                    Box(modifier = Modifier.size(6.dp).clip(CircleShape).background(MintAccent.copy(alpha = alpha3)))
                }
            }
        }
    }
}
