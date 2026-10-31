package com.example.nutrimate.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.nutrimate.ui.theme.*

@Composable
fun GlassBox(
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 24.dp,
    backgroundColor: Color = GlassCardBg,
    borderColor: Color = GlassCardBorder,
    borderWidth: Dp = 1.dp,
    content: @Composable BoxScope.() -> Unit
) {
    val shape = RoundedCornerShape(cornerRadius)
    Box(
        modifier = modifier
            .clip(shape)
            .background(backgroundColor, shape)
            .border(
                width = borderWidth,
                brush = Brush.verticalGradient(
                    colors = listOf(
                        borderColor,
                        borderColor.copy(alpha = 0.15f)
                    )
                ),
                shape = shape
            ),
        content = content
    )
}

@Composable
fun GlassButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    testTag: String = "action_button",
    enabled: Boolean = true
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(54.dp)
            .shadow(12.dp, RoundedCornerShape(18.dp), ambientColor = Color.Black.copy(alpha = 0.3f))
            .clip(RoundedCornerShape(18.dp))
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(Color(0xFFB9FFD7), Color(0xFF6AF0A6))
                )
            )
            .border(
                1.dp,
                Brush.verticalGradient(listOf(Color.White, Color.White.copy(alpha = 0.3f))),
                RoundedCornerShape(18.dp)
            )
            .clickable(enabled = enabled, onClick = onClick)
            .testTag(testTag),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = MintAccentDark,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
fun GlassToggleRow(
    title: String,
    isChecked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    testTag: String = "toggle_row"
) {
    val switchAlignment by animateFloatAsState(
        targetValue = if (isChecked) 1f else 0f,
        animationSpec = tween(durationMillis = 250),
        label = "switchAnim"
    )

    GlassBox(
        modifier = modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!isChecked) }
            .testTag(testTag),
        cornerRadius = 18.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = title,
                color = Color.White,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.weight(1f)
            )
            Box(
                modifier = Modifier
                    .width(48.dp)
                    .height(28.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(if (isChecked) Color(0xFF34C759) else Color.White.copy(alpha = 0.25f))
                    .padding(2.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .offset(x = (switchAlignment * 20).dp)
                        .clip(CircleShape)
                        .background(Color.White)
                        .shadow(4.dp, CircleShape)
                )
            }
        }
    }
}

@Composable
fun DayProgressRing(
    progressPercent: Int,
    modifier: Modifier = Modifier
) {
    val animatedProgress by animateFloatAsState(
        targetValue = (progressPercent / 100f).coerceIn(0f, 1f),
        animationSpec = tween(1000),
        label = "dayProgress"
    )

    Box(
        modifier = modifier.size(104.dp),
        contentAlignment = Alignment.Center
    ) {
        androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxSize()) {
            val strokeWidth = 9.dp.toPx()
            // Track
            drawArc(
                color = Color.White.copy(alpha = 0.2f),
                startAngle = -90f,
                sweepAngle = 360f,
                useCenter = false,
                style = Stroke(width = strokeWidth)
            )
            // Progress
            drawArc(
                color = MintAccent,
                startAngle = -90f,
                sweepAngle = 360f * animatedProgress,
                useCenter = false,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
            )
        }

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "$progressPercent%",
                color = Color.White,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "of day",
                color = Color.White.copy(alpha = 0.72f),
                fontSize = 10.sp
            )
        }
    }
}
