package com.jesse.nen.orbs

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
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
import kotlin.random.Random

// Toutes les orbes reprennent le même gabarit, calqué sur une image de référence de Tyson :
// un anneau fin et brillant, cerné d'un cocon de fibres sombres emmêlées (pas un halo rond
// lisse), lui-même noyé dans une teinte ambiante qui déborde largement dans le fond. Seules la
// teinte et la cadence distinguent une orbe d'une autre.

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
    val intensity = if (active) 1f else 0.42f
    val breath = (sin(elapsed / (if (active) 780f else 1600f)) + 1f) / 2f
    val spin = elapsed * (if (active) 0.032f else 0.010f)
    val hue = (sin(elapsed / 2600f) + 1f) / 2f
    val hot = lerp(IRIS_CYAN, IRIS_VIOLET, hue)

    HairyOrb(
        modifier = modifier.semantics {
            contentDescription = if (active) "Ten actif, toucher pour rompre"
            else "Ten dormant, toucher pour tisser"
        },
        diameter = diameter,
        seed = 1,
        washColor = hot,
        cocoonColor = hot,
        ringHot = Color.White,
        ringCool = hot,
        alphaScale = intensity,
        spinDeg = spin,
        ringAlpha = (0.92f + breath * 0.08f) * intensity,
        washAlpha = (0.42f + breath * 0.14f) * intensity
    )
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
    val breath = (sin(elapsed / (if (active) 700f else 1500f)) + 1f) / 2f
    val spin = elapsed * (if (active) 0.050f else 0.017f)

    HairyOrb(
        modifier = modifier.semantics { contentDescription = "Orbe du Pomodoro, toucher pour ouvrir" },
        diameter = diameter,
        seed = 2,
        washColor = WHITE_COOL,
        cocoonColor = WHITE_COOL,
        ringHot = Color.White,
        ringCool = WHITE_COOL,
        alphaScale = 1f,
        spinDeg = spin,
        ringAlpha = 0.95f,
        washAlpha = 0.40f + breath * 0.16f
    )
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
    val intensity = if (playing) 1f else 0.54f
    val wave = (sin(elapsed / (if (playing) 520f else 1400f)) + 1f) / 2f
    val spin = elapsed * (if (playing) 0.042f else 0.013f)

    HairyOrb(
        modifier = modifier.semantics {
            contentDescription = if (playing) "Ambiance en cours, toucher pour couper"
            else "Ambiance, toucher pour lancer"
        },
        diameter = diameter,
        seed = 3,
        washColor = AMBER_HOT,
        cocoonColor = AMBER_DEEP,
        ringHot = AMBER_HOT,
        ringCool = AMBER_DEEP,
        alphaScale = intensity,
        spinDeg = spin,
        ringAlpha = (0.92f + wave * 0.08f) * intensity,
        washAlpha = (0.40f + wave * 0.18f) * intensity,
        ringRadiusScale = 0.90f + wave * 0.07f
    )
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
    // Deux battements par cycle, comme un pouls.
    val pulse = (sin(elapsed / 300f) + 1f) / 2f
    val beat = pulse * pulse
    val spin = elapsed * 0.024f

    HairyOrb(
        modifier = modifier.semantics { contentDescription = "Faille dans la protection, toucher pour voir" },
        diameter = diameter,
        seed = 4,
        washColor = DANGER_HOT,
        cocoonColor = DANGER_DEEP,
        ringHot = DANGER_HOT,
        ringCool = DANGER_DEEP,
        alphaScale = 1f,
        spinDeg = spin,
        ringAlpha = 0.84f + beat * 0.16f,
        washAlpha = 0.36f + beat * 0.26f
    )
}

/**
 * Le Serment de Nen : l'orbe noire qui réunit tous les mots bloqués. Une seule orbe, jamais une
 * par mot — le tap (après empreinte) ouvre la liste où en ajouter et en retirer.
 *
 * Sa volute et son cocon restent presque noirs (la même encre que le fond), et son anneau
 * reprend le bleu glacé du Ten sans jamais en emprunter les teintes chaudes : c'est l'orbe la
 * plus sobre du champ, celle qui se fond le plus dans le vide qui l'entoure.
 */
@Composable
fun SermentOrb(
    modifier: Modifier = Modifier,
    diameter: Dp = 34.dp
) {
    val elapsed = rememberElapsedMillis()
    val breath = (sin(elapsed / 2200f) + 1f) / 2f
    val spin = elapsed * 0.012f

    HairyOrb(
        modifier = modifier.semantics { contentDescription = "Serment de Nen, toucher pour gérer les mots bloqués" },
        diameter = diameter,
        seed = 5,
        washColor = VOID_INDIGO,
        cocoonColor = Color.Black,
        ringHot = ICE_WHITE,
        ringCool = IRIS_BLUE,
        alphaScale = 1f,
        spinDeg = spin,
        ringAlpha = 0.80f + breath * 0.10f,
        washAlpha = 0.30f + breath * 0.10f
    )
}

/**
 * Le gabarit commun à toutes les orbes, en trois couches (du fond vers l'avant) :
 *  1. [washColor] : une teinte ambiante immense et très douce, qui déborde loin dans le fond —
 *     c'est elle qui donne l'impression que chaque orbe teinte tout un quart d'écran.
 *  2. [cocoonColor] : un cocon de fibres sombres emmêlées autour de l'anneau, flouté. Dessinées
 *     une fois par [seed] (mémorisées), puis simplement tournées ensemble — recalculer leur
 *     forme à chaque frame serait à la fois inutile et coûteux.
 *  3. l'anneau net ([ringHot]/[ringCool]), avec un point de lumière qui glisse le long du fil.
 *
 * Les deux premières couches débordent volontairement de [diameter] via `requiredSize` : Box ne
 * les recadre pas, et la taille du Box lui-même (donc la boîte de collision) ne change pas.
 */
@Composable
private fun HairyOrb(
    diameter: Dp,
    seed: Int,
    washColor: Color,
    cocoonColor: Color,
    ringHot: Color,
    ringCool: Color,
    alphaScale: Float,
    spinDeg: Float,
    ringAlpha: Float,
    washAlpha: Float,
    modifier: Modifier = Modifier,
    ringRadiusScale: Float = 0.92f
) {
    val strands = remember(seed) { buildStrands(seed) }

    Box(modifier = modifier.size(diameter), contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.requiredSize(diameter * WASH_SCALE)) {
            drawWash(washColor, washAlpha * alphaScale)
        }

        Canvas(
            modifier = Modifier
                .requiredSize(diameter * COCOON_SCALE)
                .blur(diameter * 0.05f)
        ) {
            drawCocoon(strands, cocoonColor, 0.80f * alphaScale, spinDeg)
        }

        Canvas(modifier = Modifier.size(diameter)) {
            val center = Offset(size.width / 2f, size.height / 2f)
            val radius = min(size.width, size.height) / 2f * 0.60f * ringRadiusScale
            drawRing(center, radius, width = radius * 0.11f, hot = ringHot, cool = ringCool, alpha = ringAlpha, rotationDeg = spinDeg)
            drawGlint(center, radius, spinDeg + GLINT_OFFSET_DEG, ringHot, ringAlpha, radius * 0.22f)
        }
    }
}

/** Un brin du cocon : sa position, sa longueur et son épaisseur, tirées une fois pour toutes. */
private class Strand(val angleDeg: Float, val radiusFactor: Float, val sweepDeg: Float, val widthFactor: Float)

/** Fibres tirées au hasard mais stables : le même [seed] redonne toujours le même cocon. */
private fun buildStrands(seed: Int): List<Strand> {
    val random = Random(seed)
    return List(STRAND_COUNT) {
        Strand(
            angleDeg = random.nextFloat() * 360f,
            radiusFactor = 0.85f + random.nextFloat() * 0.60f,
            sweepDeg = 14f + random.nextFloat() * 34f,
            widthFactor = 0.07f + random.nextFloat() * 0.09f
        )
    }
}

/**
 * La teinte ambiante : un unique dégradé radial, mais tiré sur un Canvas bien plus grand que
 * l'orbe elle-même, pour qu'il s'éteigne loin dans le fond au lieu de s'arrêter net au bord
 * de la boîte.
 */
private fun DrawScope.drawWash(color: Color, alpha: Float) {
    val center = Offset(size.width / 2f, size.height / 2f)
    val outerRadius = min(size.width, size.height) / 2f
    drawCircle(
        brush = Brush.radialGradient(
            0.00f to color.copy(alpha = (alpha * 0.95f).coerceIn(0f, 1f)),
            0.30f to color.copy(alpha = alpha * 0.55f),
            0.60f to color.copy(alpha = alpha * 0.22f),
            1.00f to Color.Transparent,
            center = center,
            radius = outerRadius
        ),
        radius = outerRadius,
        center = center,
        blendMode = BlendMode.Plus
    )
}

/**
 * Le cocon : les brins tirés par [buildStrands], dessinés en arcs courts à des rayons voisins
 * du fil de l'anneau (le canvas du cocon est [COCOON_SCALE] fois plus grand que celui de
 * l'anneau, d'où la division). Flouté par l'appelant : pas de blend additif ici, ces fibres
 * doivent se lire comme une matière sombre qui mange la lumière, pas comme une source.
 */
private fun DrawScope.drawCocoon(strands: List<Strand>, color: Color, alpha: Float, rotationDeg: Float) {
    val center = Offset(size.width / 2f, size.height / 2f)
    val ringRadius = (min(size.width, size.height) / COCOON_SCALE) / 2f * 0.60f

    for (strand in strands) {
        val r = ringRadius * strand.radiusFactor
        drawArc(
            color = color.copy(alpha = alpha),
            startAngle = strand.angleDeg + rotationDeg,
            sweepAngle = strand.sweepDeg,
            useCenter = false,
            topLeft = Offset(center.x - r, center.y - r),
            size = Size(r * 2f, r * 2f),
            style = Stroke(width = ringRadius * strand.widthFactor, cap = StrokeCap.Round)
        )
    }
}

/**
 * L'anneau : un cercle unique (jamais une ellipse ni plusieurs boucles croisées), avec un
 * point chaud qui glisse le long du fil. Deux passes : un trait large et pâle en diffusion,
 * puis le fil net par-dessus, pour qu'il se lise comme noyé dans sa propre lumière plutôt que
 * comme un cercle de dessin technique.
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
        ringPass(center, radius, width * 3.0f, hot, cool, alpha * 0.20f)
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

/** Le point de lumière franc qui glisse sur le fil, comme le reflet sur l'image de référence. */
private fun DrawScope.drawGlint(center: Offset, radius: Float, angleDeg: Float, color: Color, alpha: Float, size: Float) {
    val rad = Math.toRadians(angleDeg.toDouble())
    val pos = Offset(center.x + (cos(rad) * radius).toFloat(), center.y + (sin(rad) * radius).toFloat())
    drawCircle(
        brush = Brush.radialGradient(
            0.00f to color.copy(alpha = alpha),
            1.00f to Color.Transparent,
            center = pos,
            radius = size
        ),
        radius = size,
        center = pos,
        blendMode = BlendMode.Plus
    )
}

/** Nombre de brins par cocon : assez pour lire un enchevêtrement, pas assez pour peser sur le tracé. */
private const val STRAND_COUNT = 22

/** Le canvas du cocon, plus grand que l'orbe pour laisser les fibres déborder librement. */
private const val COCOON_SCALE = 2.0f

/** Le canvas de la teinte ambiante, bien plus grand encore pour qu'elle se perde dans le fond. */
private const val WASH_SCALE = 3.2f

private const val GLINT_OFFSET_DEG = 130f

private val IRIS_CYAN = Color(0xFF7FE9F5)
private val IRIS_VIOLET = Color(0xFFB08CFF)
private val IRIS_BLUE = Color(0xFF6E8BFF)

private val WHITE_COOL = Color(0xFFBFD4FF)

private val AMBER_HOT = Color(0xFFFFE0A8)
private val AMBER_DEEP = Color(0xFF9A5A1E)

private val DANGER_HOT = Color(0xFFFF9AA6)
private val DANGER_DEEP = Color(0xFF7A2233)

private val ICE_WHITE = Color(0xFFE6EEFF)
private val VOID_INDIGO = Color(0xFF141033)
