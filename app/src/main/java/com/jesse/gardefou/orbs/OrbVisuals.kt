package com.jesse.gardefou.orbs

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.jesse.gardefou.ui.rememberElapsedMillis
import kotlin.math.min
import kotlin.math.sin

// Toutes les orbes parlent la langue du fond : un cœur sombre, presque transparent, cerné de
// filaments de lumière très fins qui se croisent en boucles désaxées. Ce qui les distingue
// n'est plus la matière mais la teinte et le rythme.

/**
 * L'orbe du Ten. Trois boucles iridescentes — blanc, vert d'eau, violet — qui tournent à des
 * vitesses différentes autour d'un cœur vide.
 *
 * Protection active : les filaments brûlent et tournent vite. Protection coupée : ils
 * s'assombrissent et ralentissent, sans jamais s'éteindre tout à fait.
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
        val intensity = if (active) 1f else 0.30f
        val breath = (sin(elapsed / (if (active) 780f else 1600f)) + 1f) / 2f
        val spin = elapsed * (if (active) 0.030f else 0.009f)

        drawBloom(center, radius, IRIS_CYAN, IRIS_VIOLET, (0.24f + breath * 0.12f) * intensity)
        drawCore(center, radius, IRIS_BLUE, 0.10f * intensity)

        // Boucles presque circulaires et de rayons voisins : elles se recouvrent en une seule
        // écharpe de lumière aux franges colorées, comme l'anneau du fond. Des ellipses
        // franches donneraient un globe filaire.
        drawLoop(
            center, radius * 0.96f, flatten = 0.99f, rotation = spin,
            width = radius * 0.075f, hot = Color.White, cool = IRIS_CYAN,
            alpha = (0.90f + breath * 0.10f) * intensity
        )
        drawLoop(
            center, radius * 0.92f, flatten = 0.94f, rotation = -spin * 1.37f + 42f,
            width = radius * 0.055f, hot = IRIS_MINT, cool = IRIS_VIOLET,
            alpha = 0.70f * intensity
        )
        drawLoop(
            center, radius * 0.89f, flatten = 0.90f, rotation = spin * 0.71f + 112f,
            width = radius * 0.045f, hot = IRIS_ROSE, cool = IRIS_BLUE,
            alpha = 0.52f * intensity
        )
    }
}

/**
 * L'orbe du Pomodoro. Deux boucles blanches, et un cœur qui reste lumineux : c'est l'orbe
 * blanche, elle doit se reconnaître à sa clarté même redessinée en filaments.
 */
@Composable
fun PomodoroOrb(
    modifier: Modifier = Modifier,
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
        val breath = (sin(elapsed / (if (active) 700f else 1500f)) + 1f) / 2f
        val spin = elapsed * (if (active) 0.048f else 0.016f)

        drawBloom(center, radius, WHITE_WARM, WHITE_COOL, 0.30f + breath * 0.16f)
        drawCore(center, radius, WHITE_WARM, 0.34f + breath * 0.12f)

        drawLoop(
            center, radius * 0.96f, flatten = 0.98f, rotation = spin,
            width = radius * 0.070f, hot = Color.White, cool = WHITE_COOL,
            alpha = 0.92f
        )
        drawLoop(
            center, radius * 0.91f, flatten = 0.92f, rotation = -spin * 1.42f + 64f,
            width = radius * 0.048f, hot = Color.White, cool = WHITE_WARM,
            alpha = 0.60f
        )
    }
}

/**
 * L'orbe d'une faille : un réglage manque et fragilise la protection. Mêmes filaments, mais
 * rouges et au battement nerveux — elle attire l'œil sans hurler, et disparaît d'elle-même
 * une fois le réglage fait.
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
        val spin = elapsed * 0.022f

        drawBloom(center, radius, DANGER_HOT, DANGER_DEEP, 0.26f + beat * 0.22f)
        drawCore(center, radius, DANGER, 0.14f + beat * 0.10f)

        drawLoop(
            center, radius * 0.96f, flatten = 0.98f, rotation = spin,
            width = radius * 0.080f, hot = DANGER_HOT, cool = DANGER,
            alpha = 0.78f + beat * 0.22f
        )
        drawLoop(
            center, radius * 0.90f, flatten = 0.93f, rotation = -spin * 1.5f + 38f,
            width = radius * 0.05f, hot = DANGER, cool = DANGER_DEEP,
            alpha = 0.48f + beat * 0.16f
        )
    }
}

/**
 * La floraison autour de l'orbe. Elle culmine à la surface et s'éteint avant le bord de la
 * boîte — le halo ne doit jamais atteindre l'arête, sous peine d'y être tranché au carré.
 */
private fun DrawScope.drawBloom(
    center: Offset,
    radius: Float,
    inner: Color,
    outer: Color,
    alpha: Float
) {
    val bloomRadius = radius * 1.62f
    drawCircle(
        brush = Brush.radialGradient(
            0.00f to inner.copy(alpha = alpha * 0.35f),
            0.58f to inner.copy(alpha = alpha),
            0.78f to outer.copy(alpha = alpha * 0.55f),
            1.00f to Color.Transparent,
            center = center,
            radius = bloomRadius
        ),
        radius = bloomRadius,
        center = center,
        blendMode = BlendMode.Plus
    )
}

/**
 * Le cœur. Volontairement très faible : l'orbe doit laisser deviner le fond au travers, comme
 * la sphère noire de l'anneau laisse voir le vide.
 */
private fun DrawScope.drawCore(center: Offset, radius: Float, color: Color, alpha: Float) {
    drawCircle(
        brush = Brush.radialGradient(
            0.00f to color.copy(alpha = alpha),
            0.70f to color.copy(alpha = alpha * 0.45f),
            1.00f to Color.Transparent,
            center = center,
            radius = radius * 0.92f
        ),
        radius = radius * 0.92f,
        center = center,
        blendMode = BlendMode.Plus
    )
}

/**
 * Une boucle de filament. [flatten] l'aplatit en ellipse, ce qui la fait lire comme un cercle
 * vu de biais : c'est le croisement de plusieurs boucles d'inclinaisons différentes qui donne
 * sa profondeur à l'anneau du fond.
 *
 * Le dégradé circulaire fait courir des points chauds le long du fil ; il tourne avec la
 * boucle, si bien que la lumière semble circuler dedans.
 */
private fun DrawScope.drawLoop(
    center: Offset,
    radius: Float,
    flatten: Float,
    rotation: Float,
    width: Float,
    hot: Color,
    cool: Color,
    alpha: Float
) {
    val rx = radius
    val ry = radius * flatten
    rotate(degrees = rotation, pivot = center) {
        // Deux passes : un trait large et pâle qui fait office de diffusion, puis le fil net
        // par-dessus. Un trait seul donnerait un cercle de dessin technique, là où le fond
        // montre des fils noyés dans leur propre lumière.
        loopPass(center, rx, ry, width * 3.4f, hot, cool, alpha * 0.20f)
        loopPass(center, rx, ry, width, hot, cool, alpha)
    }
}

private fun DrawScope.loopPass(
    center: Offset,
    rx: Float,
    ry: Float,
    width: Float,
    hot: Color,
    cool: Color,
    alpha: Float
) {
    drawArc(
        brush = Brush.sweepGradient(
            0.00f to cool.copy(alpha = alpha * 0.12f),
            0.16f to hot.copy(alpha = alpha),
            0.34f to cool.copy(alpha = alpha * 0.30f),
            0.55f to hot.copy(alpha = alpha * 0.78f),
            0.72f to cool.copy(alpha = alpha * 0.18f),
            0.88f to hot.copy(alpha = alpha * 0.52f),
            1.00f to cool.copy(alpha = alpha * 0.12f),
            center = center
        ),
        startAngle = 0f,
        sweepAngle = 360f,
        useCenter = false,
        topLeft = Offset(center.x - rx, center.y - ry),
        size = Size(rx * 2f, ry * 2f),
        style = Stroke(width = width, cap = StrokeCap.Round),
        blendMode = BlendMode.Plus
    )
}

private val IRIS_CYAN = Color(0xFF7FE9F5)
private val IRIS_MINT = Color(0xFF9BF3CE)
private val IRIS_VIOLET = Color(0xFFB08CFF)
private val IRIS_BLUE = Color(0xFF6E8BFF)
private val IRIS_ROSE = Color(0xFFEFA7E6)

private val WHITE_WARM = Color(0xFFF3F6FF)
private val WHITE_COOL = Color(0xFFBFD4FF)

private val DANGER_HOT = Color(0xFFFF9AA6)
private val DANGER = Color(0xFFCF6679)
private val DANGER_DEEP = Color(0xFF7A2233)
