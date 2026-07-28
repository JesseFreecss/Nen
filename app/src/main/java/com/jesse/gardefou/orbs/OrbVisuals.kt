package com.jesse.gardefou.orbs

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.jesse.gardefou.ui.rememberElapsedMillis
import kotlin.math.min
import kotlin.math.sin

/**
 * L'orbe du Ten : une sphère iridescente qui prend les mêmes bleus, verts d'eau et violets
 * que l'anneau du fond, mais en nacre plutôt qu'en fil de lumière — elle se lit tout de suite
 * comme un objet posé devant le décor, pas comme un morceau du décor.
 *
 * Protection active : elle brille, ses reflets tournent vite. Protection coupée : elle
 * s'assombrit et ralentit, sans jamais s'éteindre tout à fait.
 */
@Composable
fun TenOrb(
    active: Boolean,
    modifier: Modifier = Modifier,
    diameter: Dp = 104.dp
) {
    val elapsed = rememberElapsedMillis()

    Canvas(
        modifier = modifier
            .size(diameter)
            .semantics {
                contentDescription = if (active) "Ten actif, toucher pour rompre"
                else "Ten dormant, toucher pour tisser"
            }
    ) {
        val center = Offset(size.width / 2f, size.height / 2f)
        val radius = min(size.width, size.height) / 2f * 0.60f
        val intensity = if (active) 1f else 0.34f
        val breath = (sin(elapsed / (if (active) 780f else 1600f)) + 1f) / 2f
        val spin = elapsed * (if (active) 0.045f else 0.014f)

        val glowRadius = radius * 1.65f
        drawCircle(
            brush = Brush.radialGradient(
                0.00f to IRIS_CYAN.copy(alpha = (0.26f + breath * 0.14f) * intensity),
                0.55f to IRIS_VIOLET.copy(alpha = (0.18f + breath * 0.10f) * intensity),
                1.00f to Color.Transparent,
                center = center,
                radius = glowRadius
            ),
            radius = glowRadius,
            center = center,
            blendMode = BlendMode.Plus
        )

        // Nacre : un dégradé circulaire qui tourne, écrêté au disque de la sphère. C'est lui
        // qui donne l'iridescence — les teintes se déplacent sur la surface sans se mélanger.
        rotate(degrees = spin, pivot = center) {
            drawCircle(
                brush = Brush.sweepGradient(
                    0.00f to IRIS_CYAN.copy(alpha = 0.85f * intensity),
                    0.22f to IRIS_MINT.copy(alpha = 0.75f * intensity),
                    0.45f to IRIS_VIOLET.copy(alpha = 0.90f * intensity),
                    0.68f to IRIS_BLUE.copy(alpha = 0.80f * intensity),
                    0.86f to IRIS_ROSE.copy(alpha = 0.70f * intensity),
                    1.00f to IRIS_CYAN.copy(alpha = 0.85f * intensity),
                    center = center
                ),
                radius = radius,
                center = center,
                blendMode = BlendMode.Plus
            )
        }

        // Cœur blanc, décentré vers le haut comme un reflet de source lumineuse.
        val coreCenter = Offset(center.x - radius * 0.12f, center.y - radius * 0.16f)
        drawCircle(
            brush = Brush.radialGradient(
                0.00f to Color.White.copy(alpha = (0.85f + breath * 0.15f) * intensity),
                0.45f to IRIS_MINT.copy(alpha = 0.45f * intensity),
                1.00f to Color.Transparent,
                center = coreCenter,
                radius = radius * 0.78f
            ),
            radius = radius * 0.78f,
            center = coreCenter,
            blendMode = BlendMode.Plus
        )

        // Liseré : la sphère garde un bord net, sinon elle se dissout dans le fond noir.
        drawCircle(
            brush = Brush.radialGradient(
                0.86f to Color.Transparent,
                0.97f to Color.White.copy(alpha = 0.55f * intensity),
                1.00f to Color.Transparent,
                center = center,
                radius = radius
            ),
            radius = radius,
            center = center,
            blendMode = BlendMode.Plus
        )
    }
}

/**
 * L'orbe d'une faille : un réglage manque et fragilise la protection. Rouge sourd, battement
 * nerveux — elle attire l'œil sans hurler, et disparaît d'elle-même une fois le réglage fait.
 */
@Composable
fun FaultOrb(
    modifier: Modifier = Modifier,
    diameter: Dp = 58.dp
) {
    val elapsed = rememberElapsedMillis()

    Canvas(
        modifier = modifier
            .size(diameter)
            .semantics { contentDescription = "Faille dans la protection, toucher pour voir" }
    ) {
        val center = Offset(size.width / 2f, size.height / 2f)
        val radius = min(size.width, size.height) / 2f * 0.60f
        // Deux battements par cycle, comme un pouls.
        val pulse = (sin(elapsed / 300f) + 1f) / 2f
        val beat = pulse * pulse

        val glowRadius = radius * 1.65f
        drawCircle(
            brush = Brush.radialGradient(
                0.00f to DANGER.copy(alpha = 0.30f + beat * 0.22f),
                0.60f to DANGER_DEEP.copy(alpha = 0.16f + beat * 0.10f),
                1.00f to Color.Transparent,
                center = center,
                radius = glowRadius
            ),
            radius = glowRadius,
            center = center,
            blendMode = BlendMode.Plus
        )

        drawCircle(
            brush = Brush.radialGradient(
                0.00f to DANGER_HOT.copy(alpha = 0.80f + beat * 0.20f),
                0.50f to DANGER.copy(alpha = 0.70f),
                0.90f to DANGER_DEEP.copy(alpha = 0.45f),
                1.00f to Color.Transparent,
                center = center,
                radius = radius
            ),
            radius = radius,
            center = center,
            blendMode = BlendMode.Plus
        )
    }
}

private val IRIS_CYAN = Color(0xFF7FE9F5)
private val IRIS_MINT = Color(0xFF9BF3CE)
private val IRIS_VIOLET = Color(0xFFB08CFF)
private val IRIS_BLUE = Color(0xFF6E8BFF)
private val IRIS_ROSE = Color(0xFFEFA7E6)

private val DANGER_HOT = Color(0xFFFF9AA6)
private val DANGER = Color(0xFFCF6679)
private val DANGER_DEEP = Color(0xFF7A2233)
