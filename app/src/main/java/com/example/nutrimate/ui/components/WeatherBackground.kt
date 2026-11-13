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
import com.example.nutrimate.data.weather.WeatherCondition
import kotlin.math.cos
import kotlin.math.sin

/**
 * Ambient, deeply blurred atmospheric aura background inspired by time-of-day and weather mood.
 * Uses soft, drifting mesh-like radial color fields rather than literal sky/weather graphics,
 * creating a calm, premium backdrop tailored for NutriMate.
 */
@Composable
fun WeatherBackground(
    condition: WeatherCondition,
    isDay: Boolean,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "ambient_aura")

    val driftPhase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = (2 * Math.PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 18000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "aura_drift"
    )

    val breathScale by infiniteTransition.animateFloat(
        initialValue = 0.92f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 7000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "aura_breath"
    )

    val palette = remember(condition, isDay) {
        getAmbientPalette(condition, isDay)
    }

    Canvas(modifier = modifier.fillMaxSize()) {
        // 1. Deep atmospheric base gradient
        drawRect(
            brush = Brush.verticalGradient(palette.baseColors)
        )

        // 2. Soft drifting primary aura orb (top-right)
        val orb1Center = Offset(
            x = size.width * (0.78f + 0.08f * cos(driftPhase)),
            y = size.height * (0.16f + 0.05f * sin(driftPhase))
        )
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    palette.primaryOrb.copy(alpha = 0.55f),
                    palette.primaryOrb.copy(alpha = 0.22f),
                    Color.Transparent
                ),
                center = orb1Center,
                radius = size.width * 0.95f * breathScale
            ),
            center = orb1Center,
            radius = size.width * 0.95f * breathScale
        )

        // 3. Soft drifting mint/teal accent orb (mid-left)
        val orb2Center = Offset(
            x = size.width * (0.18f + 0.07f * sin(driftPhase)),
            y = size.height * (0.44f + 0.06f * cos(driftPhase))
        )
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    palette.secondaryOrb.copy(alpha = 0.45f),
                    palette.secondaryOrb.copy(alpha = 0.16f),
                    Color.Transparent
                ),
                center = orb2Center,
                radius = size.width * 0.85f
            ),
            center = orb2Center,
            radius = size.width * 0.85f
        )

        // 4. Lower horizon glow orb (bottom-right)
        val orb3Center = Offset(
            x = size.width * (0.68f - 0.06f * cos(driftPhase)),
            y = size.height * (0.82f + 0.04f * sin(driftPhase))
        )
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    palette.tertiaryOrb.copy(alpha = 0.38f),
                    palette.tertiaryOrb.copy(alpha = 0.12f),
                    Color.Transparent
                ),
                center = orb3Center,
                radius = size.width * 0.9f * breathScale
            ),
            center = orb3Center,
            radius = size.width * 0.9f * breathScale
        )
    }
}

private data class AmbientPalette(
    val baseColors: List<Color>,
    val primaryOrb: Color,
    val secondaryOrb: Color,
    val tertiaryOrb: Color
)

private fun getAmbientPalette(condition: WeatherCondition, isDay: Boolean): AmbientPalette {
    return if (isDay) {
        when (condition) {
            WeatherCondition.CLEAR, WeatherCondition.PARTLY_CLOUDY -> AmbientPalette(
                baseColors = listOf(
                    Color(0xFF0A192F),
                    Color(0xFF102A43),
                    Color(0xFF133A52),
                    Color(0xFF0B1F33)
                ),
                primaryOrb = Color(0xFF3B82F6),   // Soft sky sapphire glow
                secondaryOrb = Color(0xFF10B981), // Mint/emerald NutriMate accent
                tertiaryOrb = Color(0xFFF59E0B)   // Subtle warm sunlit amber
            )
            WeatherCondition.CLOUDY, WeatherCondition.FOGGY -> AmbientPalette(
                baseColors = listOf(
                    Color(0xFF111827),
                    Color(0xFF1F2937),
                    Color(0xFF283548),
                    Color(0xFF0F172A)
                ),
                primaryOrb = Color(0xFF64748B),
                secondaryOrb = Color(0xFF14B8A6),
                tertiaryOrb = Color(0xFF475569)
            )
            WeatherCondition.DRIZZLE, WeatherCondition.RAINY, WeatherCondition.HEAVY_RAIN -> AmbientPalette(
                baseColors = listOf(
                    Color(0xFF081320),
                    Color(0xFF0F2136),
                    Color(0xFF16324A),
                    Color(0xFF091522)
                ),
                primaryOrb = Color(0xFF0EA5E9),
                secondaryOrb = Color(0xFF2DD4BF),
                tertiaryOrb = Color(0xFF334155)
            )
            WeatherCondition.THUNDERSTORM -> AmbientPalette(
                baseColors = listOf(
                    Color(0xFF0B0A1A),
                    Color(0xFF161332),
                    Color(0xFF1F1D45),
                    Color(0xFF0D1124)
                ),
                primaryOrb = Color(0xFF6366F1),
                secondaryOrb = Color(0xFF10B981),
                tertiaryOrb = Color(0xFF7C3AED)
            )
        }
    } else {
        when (condition) {
            WeatherCondition.CLEAR, WeatherCondition.PARTLY_CLOUDY -> AmbientPalette(
                baseColors = listOf(
                    Color(0xFF050B18),
                    Color(0xFF0A162D),
                    Color(0xFF0E213F),
                    Color(0xFF060E1F)
                ),
                primaryOrb = Color(0xFF2563EB),
                secondaryOrb = Color(0xFF059669),
                tertiaryOrb = Color(0xFF4F46E5)
            )
            WeatherCondition.CLOUDY, WeatherCondition.FOGGY -> AmbientPalette(
                baseColors = listOf(
                    Color(0xFF090D16),
                    Color(0xFF121A28),
                    Color(0xFF1B2638),
                    Color(0xFF0A101B)
                ),
                primaryOrb = Color(0xFF475569),
                secondaryOrb = Color(0xFF0D9488),
                tertiaryOrb = Color(0xFF334155)
            )
            WeatherCondition.DRIZZLE, WeatherCondition.RAINY, WeatherCondition.HEAVY_RAIN -> AmbientPalette(
                baseColors = listOf(
                    Color(0xFF050D17),
                    Color(0xFF0B192B),
                    Color(0xFF11263D),
                    Color(0xFF07111E)
                ),
                primaryOrb = Color(0xFF0284C7),
                secondaryOrb = Color(0xFF10B981),
                tertiaryOrb = Color(0xFF1E3A5F)
            )
            WeatherCondition.THUNDERSTORM -> AmbientPalette(
                baseColors = listOf(
                    Color(0xFF070512),
                    Color(0xFF120D29),
                    Color(0xFF1B153B),
                    Color(0xFF090B1A)
                ),
                primaryOrb = Color(0xFF7C3AED),
                secondaryOrb = Color(0xFF14B8A6),
                tertiaryOrb = Color(0xFF3730A3)
            )
        }
    }
}
