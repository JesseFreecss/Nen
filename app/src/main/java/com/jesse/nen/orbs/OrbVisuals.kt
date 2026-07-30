package com.jesse.nen.orbs

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
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.jesse.nen.ui.rememberElapsedMillis
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

// Toutes les orbes parlent la langue du fond : un cœur noir, presque transparent, cerné d'UN
// anneau fin et lumineux, lui-même enveloppé d'une volute douce façon petite nébuleuse — jamais
// plusieurs boucles qui s'entrecroisent. Ce qui les distingue n'est plus le nombre de fils mais
// la teinte et le rythme.

/**
 * L'orbe du Ten. Un anneau qui glisse du cyan au violet, comme un reflet du grand anneau du
 * fond. Protection active : il brûle et tourne vite. Protection coupée : il s'assombrit et
 * ralentit, sans jamais s'éteindre tout à fait.
 */
@Composable
fun TenOrb(
    active: Boolean,
    modifier: Modifier = Modifier,
    diameter: Dp = 52.dp
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
        val intensity = if (active) 1f else 0.42f
        val breath = (sin(elapsed / (if (active) 780f else 1600f)) + 1f) / 2f
        val spin = elapsed * (if (active) 0.032f else 0.010f)
        val hue = (sin(elapsed / 2600f) + 1f) / 2f
        val hot = lerp(IRIS_CYAN, IRIS_VIOLET, hue)
        val cool = lerp(IRIS_BLUE, IRIS_ROSE, hue)

        drawNebulaHalo(center, radius, hot, (0.42f + breath * 0.14f) * intensity, elapsed)
        drawRing(
            center, radius * 0.92f, width = radius * 0.10f,
            hot = Color.White, cool = hot, alpha = (0.92f + breath * 0.08f) * intensity,
            rotationDeg = spin
        )
    }
}

/**
 * L'orbe du Pomodoro : anneau blanc chaud, presque sans teinte — elle doit se reconnaître à sa
 * clarté, seule orbe sans couleur propre.
 */
@Composable
fun PomodoroOrb(
    modifier: Modifier = Modifier,
    diameter: Dp = 31.dp,
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
        val spin = elapsed * (if (active) 0.050f else 0.017f)

        drawNebulaHalo(center, radius, WHITE_WARM, 0.40f + breath * 0.16f, elapsed)
        drawRing(
            center, radius * 0.92f, width = radius * 0.085f,
            hot = Color.White, cool = WHITE_COOL, alpha = 0.95f,
            rotationDeg = spin
        )
    }
}

/**
 * L'orbe de l'ambiance sonore. Ambre chaud — la seule teinte tiède du champ. L'anneau respire :
 * il s'écarte et se resserre au repos, plus large et plus vite en lecture.
 */
@Composable
fun SoundOrb(
    playing: Boolean,
    modifier: Modifier = Modifier,
    diameter: Dp = 31.dp
) {
    val elapsed = rememberElapsedMillis()

    Canvas(
        modifier = modifier
            .size(diameter)
            .semantics {
                contentDescription = if (playing) "Ambiance en cours, toucher pour couper"
                else "Ambiance, toucher pour lancer"
            }
    ) {
        val center = Offset(size.width / 2f, size.height / 2f)
        val radius = min(size.width, size.height) / 2f * 0.60f
        val intensity = if (playing) 1f else 0.54f
        val wave = (sin(elapsed / (if (playing) 520f else 1400f)) + 1f) / 2f
        val spin = elapsed * (if (playing) 0.042f else 0.013f)

        drawNebulaHalo(center, radius, AMBER_HOT, (0.40f + wave * 0.18f) * intensity, elapsed)
        drawRing(
            center, radius * (0.90f + wave * 0.07f), width = radius * 0.085f,
            hot = AMBER_HOT, cool = AMBER_DEEP, alpha = (0.92f + wave * 0.08f) * intensity,
            rotationDeg = spin
        )
    }
}

/**
 * L'orbe d'une faille : un réglage manque et fragilise la protection. Même anneau, mais rouge
 * et au battement nerveux — elle attire l'œil sans hurler, et disparaît d'elle-même une fois le
 * réglage fait.
 */
@Composable
fun FaultOrb(
    modifier: Modifier = Modifier,
    diameter: Dp = 29.dp
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
        val spin = elapsed * 0.024f

        drawNebulaHalo(center, radius, DANGER_HOT, 0.36f + beat * 0.26f, elapsed)
        drawRing(
            center, radius * 0.94f, width = radius * 0.09f,
            hot = DANGER_HOT, cool = DANGER_DEEP, alpha = 0.84f + beat * 0.16f,
            rotationDeg = spin
        )
    }
}

/**
 * Le Serment de Nen : l'orbe noire qui réunit tous les mots bloqués. Une seule orbe, jamais une
 * par mot — le tap (après empreinte) ouvre la liste où en ajouter et en retirer.
 *
 * Sa volute est presque noire (la même encre que le fond), et son anneau reprend le bleu
 * glacé du Ten sans jamais en emprunter les teintes chaudes : c'est l'orbe la plus sobre du
 * champ, celle qui se fond le plus dans le vide qui l'entoure.
 */
@Composable
fun SermentOrb(
    modifier: Modifier = Modifier,
    diameter: Dp = 34.dp
) {
    val elapsed = rememberElapsedMillis()

    Canvas(
        modifier = modifier
            .size(diameter)
            .semantics { contentDescription = "Serment de Nen, toucher pour gérer les mots bloqués" }
    ) {
        val center = Offset(size.width / 2f, size.height / 2f)
        val radius = min(size.width, size.height) / 2f * 0.60f
        val breath = (sin(elapsed / 2200f) + 1f) / 2f
        val spin = elapsed * 0.012f

        drawNebulaHalo(center, radius, VOID_INDIGO, 0.30f + breath * 0.10f, elapsed)
        drawRing(
            center, radius * 0.92f, width = radius * 0.075f,
            hot = ICE_WHITE, cool = IRIS_BLUE, alpha = 0.80f + breath * 0.10f,
            rotationDeg = spin
        )
    }
}

/**
 * La volute autour de l'orbe : un halo doux, cassé en trois foyers qui dérivent lentement
 * autour du centre pour rompre la symétrie parfaite d'un dégradé radial seul — c'est ce
 * décalage qui le fait lire comme une petite nébuleuse plutôt que comme un disque flou.
 * Elle culmine bien avant le bord de la boîte, pour ne jamais y être tranchée au carré.
 */
private fun DrawScope.drawNebulaHalo(
    center: Offset,
    radius: Float,
    color: Color,
    alpha: Float,
    elapsedMs: Float
) {
    val bloomRadius = radius * 1.7f
    drawCircle(
        brush = Brush.radialGradient(
            0.00f to color.copy(alpha = alpha * 0.10f),
            0.55f to color.copy(alpha = alpha * 0.34f),
            1.00f to Color.Transparent,
            center = center,
            radius = bloomRadius
        ),
        radius = bloomRadius,
        center = center,
        blendMode = BlendMode.Plus
    )

    val wisps = 3
    val driftRadius = radius * 0.5f
    val wispRadius = radius * 0.95f
    for (i in 0 until wisps) {
        val angleDeg = elapsedMs * 0.006f + i * (360f / wisps)
        val angleRad = Math.toRadians(angleDeg.toDouble())
        val wispCenter = Offset(
            center.x + (cos(angleRad) * driftRadius).toFloat(),
            center.y + (sin(angleRad) * driftRadius).toFloat()
        )
        drawCircle(
            brush = Brush.radialGradient(
                0.00f to color.copy(alpha = alpha * 0.20f),
                1.00f to Color.Transparent,
                center = wispCenter,
                radius = wispRadius
            ),
            radius = wispRadius,
            center = wispCenter,
            blendMode = BlendMode.Plus
        )
    }
}

/**
 * L'anneau : un cercle unique (jamais une ellipse ni plusieurs boucles croisées), avec un
 * point chaud qui glisse le long du fil. Deux passes, comme avant : un trait large et pâle en
 * diffusion, puis le fil net par-dessus, pour qu'il se lise comme noyé dans sa propre lumière
 * plutôt que comme un cercle de dessin technique.
 */
private fun DrawScope.drawRing(
    center: Offset,
    radius: Float,
    width: Float,
    hot: Color,
    cool: Color,
    alpha: Float,
    rotationDeg: Float
) {
    rotate(degrees = rotationDeg, pivot = center) {
        ringPass(center, radius, width * 3.2f, hot, cool, alpha * 0.20f)
        ringPass(center, radius, width, hot, cool, alpha)
    }
}

private fun DrawScope.ringPass(
    center: Offset,
    radius: Float,
    width: Float,
    hot: Color,
    cool: Color,
    alpha: Float
) {
    drawArc(
        brush = Brush.sweepGradient(
            0.00f to cool.copy(alpha = alpha * 0.14f),
            0.16f to hot.copy(alpha = alpha),
            0.34f to cool.copy(alpha = alpha * 0.32f),
            0.55f to hot.copy(alpha = alpha * 0.80f),
            0.72f to cool.copy(alpha = alpha * 0.20f),
            0.88f to hot.copy(alpha = alpha * 0.55f),
            1.00f to cool.copy(alpha = alpha * 0.14f),
            center = center
        ),
        startAngle = 0f,
        sweepAngle = 360f,
        useCenter = false,
        topLeft = Offset(center.x - radius, center.y - radius),
        size = Size(radius * 2f, radius * 2f),
        style = Stroke(width = width, cap = StrokeCap.Round),
        blendMode = BlendMode.Plus
    )
}

private val IRIS_CYAN = Color(0xFF7FE9F5)
private val IRIS_VIOLET = Color(0xFFB08CFF)
private val IRIS_BLUE = Color(0xFF6E8BFF)
private val IRIS_ROSE = Color(0xFFEFA7E6)

private val WHITE_WARM = Color(0xFFF3F6FF)
private val WHITE_COOL = Color(0xFFBFD4FF)

private val AMBER_HOT = Color(0xFFFFE0A8)
private val AMBER_DEEP = Color(0xFF9A5A1E)

private val DANGER_HOT = Color(0xFFFF9AA6)
private val DANGER_DEEP = Color(0xFF7A2233)

private val ICE_WHITE = Color(0xFFE6EEFF)
private val VOID_INDIGO = Color(0xFF141033)
