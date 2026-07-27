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
 * Un vœu scellé, rendu illisible : une orbe noire cerclée d'un disque d'accrétion qui
 * tournoie. Le contenu n'apparaît qu'après déverrouillage par empreinte.
 *
 * L'anneau est un ovale aplati que l'on fait tourner : à cette taille, la rotation d'une
 * ellipse suffit à évoquer le vortex, sans le coût d'un anneau découpé en dizaines de
 * segments redessinés à chaque frame — il peut y avoir beaucoup d'orbes à l'écran.
 */
@Composable
fun VowOrb(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    diameter: Dp = 56.dp,
    seed: Int = 0
) {
    val elapsed = rememberElapsedMillis()
    // Décalage propre à chaque orbe : sans lui, toutes tourneraient au même angle et la
    // grille aurait l'air d'un motif imprimé plutôt que d'un ensemble d'objets vivants.
    val phase = remember(seed) { (seed * 137) % 360 }
    val speed = remember(seed) { 0.030f + (seed % 7) * 0.004f }

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
        val radius = min(size.width, size.height) / 2f * 0.56f
        val angle = (elapsed * speed + phase) % 360f
        val pulse = (sin(elapsed / 700f + seed) + 1f) / 2f

        // Halo diffus, pour que l'orbe ne soit pas posée à plat sur le fond.
        drawCircle(
            color = HALO.copy(alpha = 0.05f + pulse * 0.05f),
            radius = radius * 2.5f,
            center = center
        )

        // Disque d'accrétion : ovale aplati, dégradé balayé pour que la lumière file.
        rotate(degrees = angle, pivot = center) {
            drawOval(
                brush = Brush.sweepGradient(
                    0.00f to Color.Transparent,
                    0.18f to ACCRETION_DIM,
                    0.34f to ACCRETION_HOT,
                    0.52f to ACCRETION_DIM,
                    0.72f to Color.Transparent,
                    0.88f to ACCRETION_DIM,
                    1.00f to Color.Transparent,
                    center = center
                ),
                topLeft = Offset(center.x - radius * 1.85f, center.y - radius * 0.62f),
                size = Size(radius * 3.7f, radius * 1.24f),
                style = Stroke(width = radius * 0.30f)
            )
        }

        // Horizon : le noir couvre le milieu de l'anneau, ne laissant que les lobes.
        drawCircle(color = Color.Black, radius = radius, center = center)

        // Liseré chaud sur le bord, qui suggère la lumière courbée autour de l'horizon.
        drawCircle(
            color = ACCRETION_HOT.copy(alpha = 0.30f + pulse * 0.25f),
            radius = radius,
            center = center,
            style = Stroke(width = radius * 0.055f)
        )
    }
}

private val ACCRETION_HOT = Color(0xFFF3E4D0)
private val ACCRETION_DIM = Color(0xFF6E7C8C)
private val HALO = Color(0xFFBFD4E6)
