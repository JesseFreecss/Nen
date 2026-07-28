package com.jesse.gardefou.pomodoro

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.jesse.gardefou.ui.rememberElapsedMillis
import kotlin.math.min
import kotlin.math.sin

/**
 * L'orbe du Pomodoro : une sphère blanche luminescente qui respire. Elle se distingue au
 * premier coup d'œil des orbes de vœu, marbrées et sombres.
 *
 * Contrairement à celles-ci, pas de `RuntimeShader` : elle est unique à l'écran et quelques
 * dégradés radiaux suffisent à la faire vivre.
 */
@Composable
fun PomodoroOrb(
    modifier: Modifier = Modifier,
    // Même convention que les orbes de vœu : la sphère n'occupe que 0,30 de la boîte, le
    // reste est la marge dans laquelle le halo s'éteint.
    diameter: Dp = 62.dp,
    active: Boolean = false
) {
    val elapsed = rememberElapsedMillis()

    Canvas(
        modifier = modifier
            .size(diameter)
            .semantics { contentDescription = "Orbe du Pomodoro, toucher pour ouvrir" }
    ) {
        val center = Offset(size.width / 2f, size.height / 2f)
        val radius = min(size.width, size.height) / 2f * 0.60f
        // Respiration plus ample et plus vive quand une phase est en cours.
        val period = if (active) 900f else 1700f
        val breath = (sin(elapsed / period) + 1f) / 2f
        val swell = if (active) 0.10f else 0.05f

        val glowRadius = radius * 1.62f
        drawCircle(
            brush = Brush.radialGradient(
                0.00f to HALO.copy(alpha = 0.30f + breath * 0.16f),
                0.42f to HALO.copy(alpha = 0.16f + breath * 0.10f),
                1.00f to Color.Transparent,
                center = center,
                radius = glowRadius
            ),
            radius = glowRadius,
            center = center,
            blendMode = BlendMode.Plus
        )

        val bodyRadius = radius * (1f + breath * swell)
        drawCircle(
            brush = Brush.radialGradient(
                0.00f to Color.White,
                0.55f to CORE.copy(alpha = 0.92f),
                0.88f to HALO.copy(alpha = 0.55f),
                1.00f to Color.Transparent,
                center = center,
                radius = bodyRadius
            ),
            radius = bodyRadius,
            center = center,
            blendMode = BlendMode.Plus
        )
    }
}

private val CORE = Color(0xFFF3F6FF)
private val HALO = Color(0xFFBFD4FF)
