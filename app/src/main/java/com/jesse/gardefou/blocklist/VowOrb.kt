package com.jesse.gardefou.blocklist

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
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
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * Un vœu scellé, rendu illisible : un noyau noir enveloppé d'une énergie violette en
 * furie façon « Hollow Purple » — bandes de plasma à contre-rotation, flash blanc-violet
 * qui affleure au centre, étincelles qui filent vers le bord. Le contenu n'apparaît qu'après
 * déverrouillage par empreinte.
 *
 * Les couches lumineuses sont composées en [BlendMode.Plus] (additif) : là où deux bandes
 * se croisent, la lumière s'accumule au lieu de s'écraser en aplat, ce qui donne le halo
 * brûlant recherché sans flou GPU — indisponible sous Android 12 (minSdk 26 ici) et de
 * toute façon trop coûteux à 60 orbes animées simultanément.
 */
@Composable
fun VowOrb(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    diameter: Dp = 56.dp,
    seed: Int = 0
) {
    val elapsed = rememberElapsedMillis()
    // Déphasage, vitesse et nombre d'étincelles propres à chaque orbe : sans eux, toutes
    // brûleraient à l'unisson et la grille aurait l'air d'un motif imprimé plutôt qu'un
    // essaim d'objets vivants.
    val phase = remember(seed) { (seed * 137) % 360 }
    val speed = remember(seed) { 0.05f + (seed % 7) * 0.010f }
    val sparkCount = remember(seed) { 5 + (seed % 3) }

    Canvas(
        modifier = modifier
            .size(diameter)
            .semantics { contentDescription = "Vœu scellé, toucher pour révéler" }
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
    ) {
        val center = Offset(size.width / 2f, size.height / 2f)
        val radius = min(size.width, size.height) / 2f * 0.62f
        val angle = (elapsed * speed + phase) % 360f
        val pulse = (sin(elapsed / 380f + seed) + 1f) / 2f
        val heartbeat = (sin(elapsed / 90f + seed * 3f) + 1f) / 2f

        // Halo atmosphérique, en deux couches additives : l'énergie déborde de la
        // sphère plutôt que de s'arrêter net à son bord.
        drawCircle(
            color = EDGE_BLUE.copy(alpha = 0.05f + pulse * 0.03f),
            radius = radius * 2.6f,
            center = center,
            blendMode = BlendMode.Plus
        )
        drawCircle(
            color = VIOLET_DEEP.copy(alpha = 0.10f + pulse * 0.06f),
            radius = radius * 1.9f,
            center = center,
            blendMode = BlendMode.Plus
        )

        // Bandes de plasma à contre-rotation ; en additif, leurs croisements s'embrasent
        // au lieu de se ternir.
        rotate(degrees = angle, pivot = center) {
            drawPlasmaArc(center, radius * 0.86f, sweep = 150f, width = radius * 0.34f, alpha = 0.65f)
            rotate(degrees = 180f, pivot = center) {
                drawPlasmaArc(center, radius * 0.86f, sweep = 150f, width = radius * 0.34f, alpha = 0.65f)
            }
        }
        rotate(degrees = -angle * 1.6f, pivot = center) {
            rotate(degrees = 60f, pivot = center) {
                drawPlasmaArc(center, radius * 0.58f, sweep = 110f, width = radius * 0.24f, alpha = 0.55f)
            }
            rotate(degrees = 240f, pivot = center) {
                drawPlasmaArc(center, radius * 0.58f, sweep = 110f, width = radius * 0.24f, alpha = 0.55f)
            }
        }

        // Flash central : la lumière blanc-violet, juste avant que le noyau ne l'avale.
        drawCircle(
            brush = Brush.radialGradient(
                0.00f to CORE_FLASH.copy(alpha = 0.9f + heartbeat * 0.1f),
                0.30f to VIOLET_HOT.copy(alpha = 0.7f),
                1.00f to Color.Transparent,
                center = center,
                radius = radius * 0.55f
            ),
            radius = radius * 0.55f,
            center = center,
            blendMode = BlendMode.Plus
        )

        // Noyau : le point noir qui engloutit la lumière, l'œil du Hollow Purple.
        drawCircle(color = Color.Black, radius = radius * 0.30f, center = center)

        // Étincelles : traits courts qui filent vers le bord, scintillent, s'éteignent —
        // le seuil sur sparkFlicker fait qu'elles clignotent au lieu de respirer en boucle.
        rotate(degrees = angle * 0.4f, pivot = center) {
            for (i in 0 until sparkCount) {
                val sparkAngle = i * (360f / sparkCount)
                val sparkFlicker = (sin(elapsed / 140f + seed + i * 2f) + 1f) / 2f
                if (sparkFlicker > 0.35f) {
                    drawSpark(center, radius, sparkAngle, sparkFlicker)
                }
            }
        }

        // Liseré : bord vif pour détacher la sphère du fond, comme la frontière visible
        // d'une déflagration contenue.
        drawCircle(
            color = RIM_GLOW.copy(alpha = 0.35f + pulse * 0.25f),
            radius = radius,
            center = center,
            style = Stroke(width = radius * 0.05f),
            blendMode = BlendMode.Plus
        )
    }
}

/** Une bande de plasma localement estompée à ses deux extrémités. */
private fun DrawScope.drawPlasmaArc(
    center: Offset,
    radius: Float,
    sweep: Float,
    width: Float,
    alpha: Float
) {
    val fadeAt = sweep / 360f
    drawArc(
        brush = Brush.sweepGradient(
            0.00f to Color.Transparent,
            fadeAt * 0.5f to VIOLET_HOT.copy(alpha = alpha),
            fadeAt to Color.Transparent,
            1.00f to Color.Transparent,
            center = center
        ),
        startAngle = 0f,
        sweepAngle = sweep,
        useCenter = false,
        topLeft = Offset(center.x - radius, center.y - radius),
        size = Size(radius * 2f, radius * 2f),
        style = Stroke(width = width, cap = StrokeCap.Round),
        blendMode = BlendMode.Plus
    )
}

/** Une étincelle radiale : un trait court, brillant, qui file vers l'extérieur. */
private fun DrawScope.drawSpark(center: Offset, radius: Float, angleDeg: Float, intensity: Float) {
    val theta = Math.toRadians(angleDeg.toDouble())
    val innerR = radius * 0.95f
    val outerR = radius * (1.15f + intensity * 0.35f)
    val start = Offset(
        center.x + (innerR * cos(theta)).toFloat(),
        center.y + (innerR * sin(theta)).toFloat()
    )
    val end = Offset(
        center.x + (outerR * cos(theta)).toFloat(),
        center.y + (outerR * sin(theta)).toFloat()
    )
    drawLine(
        color = SPARK_COLOR.copy(alpha = intensity * 0.85f),
        start = start,
        end = end,
        strokeWidth = radius * 0.05f,
        cap = StrokeCap.Round,
        blendMode = BlendMode.Plus
    )
}

private val CORE_FLASH = Color(0xFFEDE0FF)
private val VIOLET_HOT = Color(0xFFA537FF)
private val VIOLET_DEEP = Color(0xFF3D0A66)
private val RIM_GLOW = Color(0xFF9D4CFF)
private val EDGE_BLUE = Color(0xFF3B5BFF)
private val SPARK_COLOR = Color(0xFFE9D6FF)
