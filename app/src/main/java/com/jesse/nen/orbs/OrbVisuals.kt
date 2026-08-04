package com.jesse.nen.orbs

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.jesse.nen.R
import com.jesse.nen.ui.rememberElapsedMillis
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

// Toutes les orbes reprennent le même gabarit, calqué sur une image de référence de Tyson :
// un anneau fin et brillant, cerné d'un cocon de fibres sombres emmêlées (texture peinte,
// fournie par Tyson dans drawable-nodpi), lui-même noyé dans une teinte ambiante qui déborde
// largement dans le fond. Seules la teinte et la cadence distinguent une orbe d'une autre.

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
        textureRes = R.drawable.orb_cyan,
        seed = 1,
        washColor = hot,
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
        textureRes = R.drawable.orb_ice,
        seed = 2,
        washColor = WHITE_COOL,
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
 * il s'écarte et se resserre au repos comme au rythme d'une respiration, en lecture il bat
 * plus large et plus vite.
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
        textureRes = R.drawable.orb_amber,
        seed = 3,
        washColor = AMBER_HOT,
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
 *
 * Réutilise provisoirement la texture violette (aucune texture rouge dédiée n'existe encore) :
 * seules les couleurs de l'anneau et de la teinte signalent le danger.
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
        textureRes = R.drawable.orb_violet,
        seed = 4,
        washColor = DANGER_HOT,
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
 * Sa teinte ambiante reste presque noire (la même encre que le fond), et son anneau reprend le
 * bleu glacé du Ten sans jamais en emprunter les teintes chaudes : c'est l'orbe la plus sobre
 * du champ, celle qui se fond le plus dans le vide qui l'entoure. Réutilise elle aussi la
 * texture violette, désaturée par les couleurs d'anneau/teinte plutôt que par une texture dédiée.
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
        textureRes = R.drawable.orb_violet,
        seed = 5,
        washColor = VOID_INDIGO,
        ringHot = ICE_WHITE,
        ringCool = IRIS_BLUE,
        alphaScale = 1f,
        spinDeg = spin,
        ringAlpha = 0.80f + breath * 0.10f,
        washAlpha = 0.30f + breath * 0.10f
    )
}

/**
 * Budgets quotidiens des vidéos courtes (Reels Instagram, Shorts YouTube) : sobre comme le
 * Serment, mais franchement noire là où le Serment reste indigo/glacé — sans quoi les deux se
 * confondraient. Réutilise la même texture violette faute d'alternative dédiée, mais avec un
 * `alphaScale` très réduit qui étouffe sa teinte propre (le violet n'apparaît qu'en fond très
 * discret) ; l'anneau passe au gris graphite plutôt qu'au bleu glacé du Serment, seul élément
 * qui reste net.
 */
@Composable
fun ShortFormOrb(
    modifier: Modifier = Modifier,
    diameter: Dp = 31.dp
) {
    val elapsed = rememberElapsedMillis()
    val breath = (sin(elapsed / 2200f) + 1f) / 2f
    val spin = elapsed * 0.012f

    HairyOrb(
        modifier = modifier.semantics {
            contentDescription = "Limites vidéos courtes, toucher pour régler"
        },
        diameter = diameter,
        textureRes = R.drawable.orb_violet,
        seed = 6,
        washColor = Color.Black,
        ringHot = GRAPHITE_LIGHT,
        ringCool = GRAPHITE_DARK,
        alphaScale = 0.40f,
        spinDeg = spin,
        ringAlpha = 0.80f + breath * 0.10f,
        washAlpha = 0.22f + breath * 0.08f
    )
}

/**
 * Le gabarit commun à toutes les orbes, en trois couches (du fond vers l'avant) :
 *  1. [washColor] : une teinte ambiante immense et très douce, qui déborde loin dans le fond —
 *     c'est elle qui donne l'impression que chaque orbe teinte tout un quart d'écran.
 *  2. une texture de vortex peinte ([textureRes]), découpée en couches animées indépendamment
 *     (voir [drawLivingTexture]) — c'est le cocon de fibres sombres.
 *  3. l'anneau net ([ringHot]/[ringCool]), avec un point de lumière qui glisse le long du fil.
 *
 * Les deux premières couches débordent volontairement de [diameter] via `requiredSize` : Box ne
 * les recadre pas, et la taille du Box lui-même (donc la boîte de collision) ne change pas.
 */
@Composable
private fun HairyOrb(
    diameter: Dp,
    textureRes: Int,
    seed: Int,
    washColor: Color,
    ringHot: Color,
    ringCool: Color,
    alphaScale: Float,
    spinDeg: Float,
    ringAlpha: Float,
    washAlpha: Float,
    modifier: Modifier = Modifier,
    ringRadiusScale: Float = 0.92f
) {
    val texture = ImageBitmap.imageResource(textureRes)
    val organicPhase = spinDeg * 0.72f
    // Rotation d'ensemble, plus lente que le glissement du point chaud sur l'anneau : l'orbe
    // tourne sur son propre axe pendant qu'elle dérive dans le champ (physique inchangée,
    // gérée ailleurs par OrbEngine). Le sens alterne selon la parité de seed pour que toutes
    // les orbes ne tournent pas de concert.
    val selfSpinDeg = spinDeg * SELF_SPIN_FACTOR * (if (seed % 2 == 0) 1f else -1f)

    Box(
        modifier = modifier
            .size(diameter)
            .graphicsLayer { rotationZ = selfSpinDeg },
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.requiredSize(diameter * WASH_SCALE)) {
            drawWash(washColor, washAlpha * alphaScale)
        }

        Canvas(modifier = Modifier.requiredSize(diameter * TEXTURE_SCALE)) {
            val center = Offset(size.width / 2f, size.height / 2f)
            val breath = sin(Math.toRadians((organicPhase * 1.7f + seed * 41f).toDouble())).toFloat()
            val innerRadius = min(size.width, size.height) * 0.235f * ringRadiusScale

            drawLivingTexture(
                texture = texture,
                center = center,
                innerRadius = innerRadius,
                phase = organicPhase,
                breath = breath,
                alpha = alphaScale
            )
            drawCore(center, innerRadius, ringCool, alphaScale, organicPhase)

            val radius = innerRadius
            drawRing(center, radius, width = radius * 0.11f, hot = ringHot, cool = ringCool, alpha = ringAlpha, rotationDeg = spinDeg)
            drawGlint(center, radius, spinDeg + GLINT_OFFSET_DEG, ringHot, ringAlpha, radius * 0.22f)
        }
    }
}

/**
 * Anime la texture peinte sans la transformer en roue rigide :
 *  - la matière extérieure oscille lentement (respiration asymétrique) ;
 *  - une copie très légère circule en sens inverse, limitée au cocon extérieur ;
 *  - le cœur dérive séparément, ce qui donne une profondeur presque liquide.
 * Le noir de la texture disparaît grâce à `BlendMode.Screen` : seules les zones lumineuses
 * de la peinture s'ajoutent au fond, le fond noir de la texture reste neutre.
 */
private fun DrawScope.drawLivingTexture(
    texture: ImageBitmap,
    center: Offset,
    innerRadius: Float,
    phase: Float,
    breath: Float,
    alpha: Float
) {
    val destination = IntSize(size.width.roundToInt(), size.height.roundToInt())
    val outsideRing = Path().apply {
        fillType = PathFillType.EvenOdd
        addRect(Rect(Offset.Zero, size))
        addOval(Rect(center = center, radius = innerRadius * 1.23f))
    }
    val core = Path().apply {
        addOval(Rect(center = center, radius = innerRadius * 0.94f))
    }

    // Texture principale : presque immobile, avec une respiration asymétrique.
    withTransform({
        rotate(degrees = phase * 0.12f, pivot = center)
        scale(
            scaleX = 1f + breath * 0.018f,
            scaleY = 1f - breath * 0.013f,
            pivot = center
        )
    }) {
        drawImage(
            image = texture,
            dstOffset = IntOffset.Zero,
            dstSize = destination,
            alpha = alpha,
            blendMode = BlendMode.Screen
        )
    }

    // Courant secondaire, limité à la matière périphérique (hors anneau).
    clipPath(outsideRing) {
        rotate(degrees = -phase * 0.21f, pivot = center) {
            scale(1.018f + breath * 0.008f, pivot = center) {
                drawImage(
                    image = texture,
                    dstOffset = IntOffset.Zero,
                    dstSize = destination,
                    alpha = 0.20f * alpha,
                    blendMode = BlendMode.Screen
                )
            }
        }
    }

    // Le verre central dérive indépendamment du vortex extérieur.
    clipPath(core) {
        rotate(degrees = phase * 0.34f, pivot = center) {
            drawImage(
                image = texture,
                dstOffset = IntOffset.Zero,
                dstSize = destination,
                alpha = 0.22f * alpha,
                blendMode = BlendMode.Screen
            )
        }
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

/** Verre sombre au centre : il donne de la profondeur sans masquer le fond ni créer un disque plat. */
private fun DrawScope.drawCore(
    center: Offset,
    radius: Float,
    color: Color,
    alphaScale: Float,
    phase: Float
) {
    val drift = Offset(
        center.x + cos(Math.toRadians(phase.toDouble())).toFloat() * radius * 0.12f,
        center.y + sin(Math.toRadians((phase * 0.83f).toDouble())).toFloat() * radius * 0.10f
    )
    drawCircle(
        brush = Brush.radialGradient(
            0.00f to color.copy(alpha = 0.18f * alphaScale),
            0.52f to color.copy(alpha = 0.075f * alphaScale),
            1.00f to Color.Transparent,
            center = drift,
            radius = radius * 0.92f
        ),
        radius = radius * 0.91f,
        center = center,
        blendMode = BlendMode.Plus
    )
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

/** Le canvas de la teinte ambiante, bien plus grand encore pour qu'elle se perde dans le fond. */
private const val WASH_SCALE = 3.2f

/** Le canvas de la texture, un peu plus grand que l'orbe pour laisser le cocon déborder. */
private const val TEXTURE_SCALE = 1.55f

private const val GLINT_OFFSET_DEG = 130f

/** Fraction de [spinDeg] appliquée à la rotation d'ensemble de l'orbe sur son propre axe. */
private const val SELF_SPIN_FACTOR = 0.35f

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

private val GRAPHITE_LIGHT = Color(0xFFCACACA)
private val GRAPHITE_DARK = Color(0xFF2B2B2E)
