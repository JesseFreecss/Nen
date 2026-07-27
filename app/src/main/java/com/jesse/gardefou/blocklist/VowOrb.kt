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

/**
 * Un vœu scellé, rendu illisible : une sphère d'énergie noire tourbillonnante, façon
 * Rasengan — deux paires de bandes tournent à des vitesses opposées autour d'un noyau
 * sombre. Le contenu n'apparaît qu'après déverrouillage par empreinte.
 *
 * Les bandes sont de simples arcs (pas un Path recalculé point par point à chaque
 * frame) : à cette taille l'effet de turbulence se lit tout aussi bien, sans le coût
 * d'une géométrie reconstruite en continu — il peut y avoir beaucoup d'orbes à l'écran.
 */
@Composable
fun VowOrb(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    diameter: Dp = 56.dp,
    seed: Int = 0
) {
    val elapsed = rememberElapsedMillis()
    // Décalage et vitesse propres à chaque orbe : sans eux, toutes tourneraient à
    // l'unisson et la grille aurait l'air d'un motif imprimé plutôt qu'un ensemble
    // d'objets vivants.
    val phase = remember(seed) { (seed * 137) % 360 }
    val speed = remember(seed) { 0.05f + (seed % 7) * 0.010f }

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
        val pulse = (sin(elapsed / 650f + seed) + 1f) / 2f

        // Halo diffus, pour que la sphère ne soit pas posée à plat sur le fond.
        drawCircle(
            color = HALO.copy(alpha = 0.07f + pulse * 0.05f),
            radius = radius * 2.2f,
            center = center
        )

        // Noyau : dégradé radial sombre, pour donner du volume à la sphère.
        drawCircle(
            brush = Brush.radialGradient(
                0.00f to CORE_LIGHT,
                0.40f to CORE_MID,
                1.00f to Color.Black,
                center = center,
                radius = radius
            ),
            radius = radius,
            center = center
        )

        // Bandes extérieures : la turbulence rapide, proche de la surface.
        rotate(degrees = angle, pivot = center) {
            drawTurbulenceArc(center, radius * 0.80f, sweep = 130f, width = radius * 0.30f, alpha = 0.55f)
            rotate(degrees = 180f, pivot = center) {
                drawTurbulenceArc(center, radius * 0.80f, sweep = 130f, width = radius * 0.30f, alpha = 0.55f)
            }
        }

        // Bandes intérieures : contre-rotation à vitesse différente, pour casser la
        // symétrie et donner une impression de compression plutôt que de rotation
        // mécanique — comme les couches d'un Rasengan.
        rotate(degrees = -angle * 1.4f, pivot = center) {
            rotate(degrees = 60f, pivot = center) {
                drawTurbulenceArc(center, radius * 0.48f, sweep = 100f, width = radius * 0.22f, alpha = 0.40f)
            }
            rotate(degrees = 240f, pivot = center) {
                drawTurbulenceArc(center, radius * 0.48f, sweep = 100f, width = radius * 0.22f, alpha = 0.40f)
            }
        }

        // Liseré : bord légèrement éclairci pour détacher la sphère du fond.
        drawCircle(
            color = RIM.copy(alpha = 0.22f + pulse * 0.18f),
            radius = radius,
            center = center,
            style = Stroke(width = radius * 0.05f)
        )
    }
}

/** Un arc localement estompé à ses deux extrémités, pour évoquer une bande d'énergie. */
private fun DrawScope.drawTurbulenceArc(
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
            fadeAt * 0.5f to ARM_COLOR.copy(alpha = alpha),
            fadeAt to Color.Transparent,
            1.00f to Color.Transparent,
            center = center
        ),
        startAngle = 0f,
        sweepAngle = sweep,
        useCenter = false,
        topLeft = Offset(center.x - radius, center.y - radius),
        size = Size(radius * 2f, radius * 2f),
        style = Stroke(width = width, cap = StrokeCap.Round)
    )
}

private val CORE_LIGHT = Color(0xFF2A2A30)
private val CORE_MID = Color(0xFF15151A)
private val ARM_COLOR = Color(0xFF48484F)
private val RIM = Color(0xFF87878F)
private val HALO = Color(0xFF3A3A42)
