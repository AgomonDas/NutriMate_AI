package com.example.nutrimate.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import com.example.nutrimate.data.weather.WeatherCondition
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/**
 * Edge-to-edge Google Weather-styled animated sky background.
 * Paints rich atmospheric gradients that correlate to real-time weather and day/night state,
 * with hardware-accelerated animated weather effects (sun rays, rain streaks, twinkling stars, clouds).
 */
@Composable
fun WeatherBackground(
    condition: WeatherCondition,
    isDay: Boolean,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "weather_anim")

    // Continuous looping animation drivers
    val animProgress by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2400, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rain_and_clouds"
    )

    val pulseProgress by infiniteTransition.animateFloat(
        initialValue = 0.85f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 3500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "sun_pulse"
    )

    val skyColors = remember(condition, isDay) {
        getSkyGradientColors(condition, isDay)
    }

    // Pre-generate stable random stars for night
    val stars = remember {
        List(45) {
            StarData(
                xNorm = Random.nextFloat(),
                yNorm = Random.nextFloat() * 0.65f,
                radius = Random.nextFloat() * 2f + 1f,
                alphaPhase = Random.nextFloat() * 6.28f
            )
        }
    }

    // Pre-generate stable rain streaks
    val raindrops = remember {
        List(60) {
            RaindropData(
                xNorm = Random.nextFloat(),
                startYNorm = Random.nextFloat(),
                length = Random.nextFloat() * 24f + 16f,
                speed = Random.nextFloat() * 0.7f + 0.8f
            )
        }
    }

    Canvas(modifier = modifier.fillMaxSize()) {
        // 1. Draw Edge-to-Edge Sky Gradient
        drawRect(
            brush = Brush.verticalGradient(skyColors)
        )

        // 2. Render condition-specific weather effects
        when (condition) {
            WeatherCondition.CLEAR, WeatherCondition.PARTLY_CLOUDY -> {
                if (isDay) {
                    drawSunnyFlare(pulseProgress)
                    if (condition == WeatherCondition.PARTLY_CLOUDY) {
                        drawFloatingClouds(animProgress)
                    }
                } else {
                    drawStarryNight(stars, pulseProgress)
                }
            }

            WeatherCondition.CLOUDY, WeatherCondition.FOGGY -> {
                drawFloatingClouds(animProgress)
                drawOvercastLayer(condition == WeatherCondition.FOGGY)
            }

            WeatherCondition.DRIZZLE, WeatherCondition.RAINY, WeatherCondition.HEAVY_RAIN, WeatherCondition.THUNDERSTORM -> {
                drawRainStreaks(raindrops, animProgress, condition)
                if (condition == WeatherCondition.THUNDERSTORM) {
                    drawLightningFlash(pulseProgress)
                }
            }
        }
    }
}

private fun getSkyGradientColors(condition: WeatherCondition, isDay: Boolean): List<Color> {
    return if (isDay) {
        when (condition) {
            WeatherCondition.CLEAR -> listOf(
                Color(0xFF1565C0), // Rich blue
                Color(0xFF1E88E5),
                Color(0xFF42A5F5),
                Color(0xFF90CAF9),
                Color(0xFFFFF9C4).copy(alpha = 0.5f) // Warm golden morning horizon
            )
            WeatherCondition.PARTLY_CLOUDY -> listOf(
                Color(0xFF1976D2),
                Color(0xFF2196F3),
                Color(0xFF64B5F6),
                Color(0xFFBBDEFB)
            )
            WeatherCondition.CLOUDY, WeatherCondition.FOGGY -> listOf(
                Color(0xFF37474F), // Overcast slate
                Color(0xFF455A64),
                Color(0xFF607D8B),
                Color(0xFF90A4AE)
            )
            WeatherCondition.DRIZZLE -> listOf(
                Color(0xFF263238),
                Color(0xFF37474F),
                Color(0xFF455A64),
                Color(0xFF546E7A)
            )
            WeatherCondition.RAINY, WeatherCondition.HEAVY_RAIN -> listOf(
                Color(0xFF1A252F), // Moody storm
                Color(0xFF2C3E50),
                Color(0xFF34495E),
                Color(0xFF415B76)
            )
            WeatherCondition.THUNDERSTORM -> listOf(
                Color(0xFF120E22), // Turbulent violet
                Color(0xFF1E1738),
                Color(0xFF2B204F),
                Color(0xFF3B2E68)
            )
        }
    } else {
        // Night Skies
        when (condition) {
            WeatherCondition.CLEAR, WeatherCondition.PARTLY_CLOUDY -> listOf(
                Color(0xFF060914), // Deep space
                Color(0xFF0C142B),
                Color(0xFF142042),
                Color(0xFF1C2C59)
            )
            WeatherCondition.CLOUDY, WeatherCondition.FOGGY -> listOf(
                Color(0xFF0F141C),
                Color(0xFF1A222E),
                Color(0xFF263040),
                Color(0xFF323F52)
            )
            WeatherCondition.DRIZZLE, WeatherCondition.RAINY, WeatherCondition.HEAVY_RAIN -> listOf(
                Color(0xFF0A1017),
                Color(0xFF131D28),
                Color(0xFF1C2B3A),
                Color(0xFF283B4F)
            )
            WeatherCondition.THUNDERSTORM -> listOf(
                Color(0xFF0A0716),
                Color(0xFF140E28),
                Color(0xFF1E163B),
                Color(0xFF2C1F52)
            )
        }
    }
}

private fun DrawScope.drawSunnyFlare(pulse: Float) {
    val sunCenter = Offset(size.width * 0.82f, size.height * 0.16f)
    val baseRadius = size.width * 0.18f

    // Outer warm glow ring
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(
                Color(0x66FFF59D),
                Color(0x33FFE082),
                Color(0x00FFD54F)
            ),
            center = sunCenter,
            radius = baseRadius * 2.4f * pulse
        ),
        center = sunCenter,
        radius = baseRadius * 2.4f * pulse
    )

    // Inner bright sun orb
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(
                Color(0xFFFFFDE7),
                Color(0xFFFFF176),
                Color(0x80FFD54F)
            ),
            center = sunCenter,
            radius = baseRadius * pulse
        ),
        center = sunCenter,
        radius = baseRadius * pulse
    )
}

private fun DrawScope.drawStarryNight(stars: List<StarData>, pulse: Float) {
    // Soft moon glow at top right
    val moonCenter = Offset(size.width * 0.85f, size.height * 0.14f)
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(
                Color(0x44E0E7FF),
                Color(0x22C7D2FE),
                Color(0x00A5B4FC)
            ),
            center = moonCenter,
            radius = size.width * 0.35f
        ),
        center = moonCenter,
        radius = size.width * 0.35f
    )

    // Twinkling stars
    for (star in stars) {
        val twinkle = (sin(star.alphaPhase + pulse * 3.14f) + 1f) / 2f
        val alpha = (0.25f + twinkle * 0.75f).coerceIn(0.1f, 1f)
        drawCircle(
            color = Color.White.copy(alpha = alpha),
            radius = star.radius,
            center = Offset(star.xNorm * size.width, star.yNorm * size.height)
        )
    }
}

private fun DrawScope.drawFloatingClouds(progress: Float) {
    val cloudColor = Color.White.copy(alpha = 0.18f)
    val width = size.width
    val height = size.height

    // Two drifting cloud puff paths
    val offset1 = (progress * width) - (width * 0.3f)
    val offset2 = ((progress + 0.5f) % 1f) * width - (width * 0.2f)

    drawCircle(
        color = cloudColor,
        radius = width * 0.28f,
        center = Offset(offset1, height * 0.18f)
    )
    drawCircle(
        color = cloudColor,
        radius = width * 0.22f,
        center = Offset(offset1 + width * 0.25f, height * 0.20f)
    )

    drawCircle(
        color = cloudColor.copy(alpha = 0.12f),
        radius = width * 0.32f,
        center = Offset(offset2, height * 0.32f)
    )
}

private fun DrawScope.drawOvercastLayer(isFog: Boolean) {
    val tint = if (isFog) Color.White.copy(alpha = 0.15f) else Color.Black.copy(alpha = 0.10f)
    drawRect(color = tint)
}

private fun DrawScope.drawRainStreaks(
    drops: List<RaindropData>,
    progress: Float,
    condition: WeatherCondition
) {
    val streakColor = Color(0xCCB0BEC5)
    val width = size.width
    val height = size.height
    val isHeavy = condition == WeatherCondition.HEAVY_RAIN || condition == WeatherCondition.THUNDERSTORM

    for (drop in drops) {
        val currentYNorm = (drop.startYNorm + progress * drop.speed) % 1.0f
        val x = drop.xNorm * width - (currentYNorm * 40f) // Angled fall
        val y = currentYNorm * height
        val length = if (isHeavy) drop.length * 1.6f else drop.length

        drawLine(
            color = streakColor,
            start = Offset(x, y),
            end = Offset(x - 8f, y + length),
            strokeWidth = if (isHeavy) 2.2f else 1.4f
        )
    }
}

private fun DrawScope.drawLightningFlash(pulse: Float) {
    if (pulse > 1.10f) {
        val flashAlpha = ((pulse - 1.10f) * 6f).coerceIn(0f, 0.35f)
        drawRect(color = Color(0xFFE0E7FF).copy(alpha = flashAlpha))
    }
}

private data class StarData(
    val xNorm: Float,
    val yNorm: Float,
    val radius: Float,
    val alphaPhase: Float
)

private data class RaindropData(
    val xNorm: Float,
    val startYNorm: Float,
    val length: Float,
    val speed: Float
)
