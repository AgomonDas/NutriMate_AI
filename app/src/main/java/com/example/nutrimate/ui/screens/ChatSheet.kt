package com.example.nutrimate.ui.screens

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Close
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

    LaunchedEffect(uiState.chatMessages.size) {
        if (uiState.chatMessages.isNotEmpty()) {
            listState.animateScrollToItem(uiState.chatMessages.size - 1)
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
                .clip(RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp))
                .background(Color(0xFF0F1A30))
                .border(
                    width = 1.dp,
                    brush = Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.35f), Color.Transparent)),
                    shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp)
                )
                .clickable(enabled = false) {} // prevent dismissing when tapping inside
                .padding(horizontal = 20.dp, vertical = 18.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        text = "NutriMate AI",
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Text(
                        text = "Can check & update your plan in real time",
                        fontSize = 12.sp,
                        color = Color.White.copy(alpha = 0.72f)
                    )
                }

                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .size(36.dp)
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

            Spacer(modifier = Modifier.height(12.dp))

            // Messages List
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
                        onDeclineTradeoff = { token -> viewModel.declineInChatTradeoff(token) }
                    )
                }
            }

            // Quick suggestion chips
            val quickChips = listOf(
                "🍛 I want biriyani for lunch",
                "🥪 Swap snack to fruit cart",
                "💸 Cheaper dinner",
                "💪 Is protein shake worth it?"
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                quickChips.forEach { chipText ->
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(16.dp))
                            .background(Color.White.copy(alpha = 0.12f))
                            .border(1.dp, Color.White.copy(alpha = 0.25f), RoundedCornerShape(16.dp))
                            .clickable {
                                viewModel.sendChatMessage(chipText)
                            }
                            .padding(horizontal = 12.dp, vertical = 7.dp)
                    ) {
                        Text(
                            text = chipText,
                            fontSize = 12.sp,
                            color = Color.White,
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
                            "Message… e.g. I want biriyani for lunch",
                            color = Color.White.copy(alpha = 0.5f),
                            fontSize = 14.sp
                        )
                    },
                    singleLine = true,
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
                            Brush.verticalGradient(listOf(Color(0xFFB9FFD7), Color(0xFF6AF0A6)))
                        )
                        .clickable {
                            if (inputText.isNotBlank()) {
                                viewModel.sendChatMessage(inputText)
                                inputText = ""
                            }
                        }
                        .testTag("chat_send_btn"),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Send,
                        contentDescription = "Send",
                        tint = MintAccentDark,
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
    onDeclineTradeoff: (String) -> Unit = {}
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
                Column(modifier = Modifier.widthIn(max = 310.dp)) {
                    GlassBox(
                        modifier = Modifier.fillMaxWidth(),
                        cornerRadius = 18.dp,
                        backgroundColor = Color.White.copy(alpha = 0.14f),
                        borderColor = Color.White.copy(alpha = 0.3f)
                    ) {
                        Text(
                            text = message.content,
                            color = Color.White,
                            fontSize = 14.sp,
                            lineHeight = 20.sp,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 11.dp)
                        )
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
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Start
            ) {
                val toolIcon = when (message.toolName) {
                    "search_menu" -> "🔍"
                    "check" -> "⚖️"
                    "find_funding" -> "💡"
                    "update" -> "🔒"
                    "gatekeeper.confirm" -> "🔓"
                    "get_alternatives" -> "🥗"
                    else -> "⚙️"
                }

                Box(
                    modifier = Modifier
                        .widthIn(max = 330.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFF070E1A))
                        .border(1.dp, MintAccent.copy(alpha = 0.55f), RoundedCornerShape(12.dp))
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
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

                        if (message.toolArgs != null) {
                            Text(
                                text = "args: (${message.toolArgs})",
                                color = Color.White.copy(alpha = 0.7f),
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace,
                                lineHeight = 14.sp
                            )
                        }

                        if (message.toolResult != null) {
                            Text(
                                text = "→ ${message.toolResult}",
                                color = MintAccentGlow,
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace,
                                lineHeight = 14.sp
                            )
                        } else {
                            Text(
                                text = message.content,
                                color = MintAccentGlow,
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace,
                                lineHeight = 14.sp
                            )
                        }
                    }
                }
            }
        }
    }
}
