package com.jesse.gardefou.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.jesse.gardefou.R
import kotlin.math.sin
import kotlin.random.Random

/**
 * Écran d'accueil : la matérialisation de Guanyin, immobile, sous une aura vivante.
 *
 * L'image de fond est FIXE — Nétéro et Guanyin ne bougent pas. Ce qui vit, c'est une couche
 * de grains d'or dessinée par-dessus, plus une respiration lumineuse d'ensemble.
 *
 * Limite assumée : les pixels de l'aura peinte ne peuvent pas être déformés ici. Le faire
 * demanderait un shader AGSL, indisponible avant Android 13 alors que l'app descend à
 * l'API 26 ; ce serait deux chemins de rendu à maintenir pour un gain discutable.
 */
@Composable
fun NeteroGate(onEnter: () -> Unit, modifier: Modifier = Modifier) {
    val elapsed = rememberElapsedMillis()

    // Positions et rythmes tirés une fois : régénérer à chaque frame ferait scintiller
    // l'ensemble au lieu de le faire dériver.
    val motes = remember { List(MOTE_COUNT) { Mote.random() } }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
            // Pas d'effet d'ondulation : un halo de clic gris sur cette image serait un
            // corps étranger.
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onEnter
            )
    ) {
        Image(
            painter = painterResource(R.drawable.neteo_gate),
            contentDescription = "Nétéro en méditation devant la matérialisation de Guanyin",
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )

        Box(
            modifier = Modifier
                .fillMaxSize()
                .drawBehind {
                    val time = elapsed / 1000f

                    // Respiration : un voile d'or dont l'intensité enfle et retombe. C'est
                    // ce qui donne à l'aura peinte l'air de palpiter.
                    val breath = (sin(time * BREATH_SPEED) + 1f) / 2f
                    drawRect(
                        color = AURA_GOLD.copy(alpha = 0.03f + breath * 0.07f),
                        size = size
                    )

                    for (mote in motes) {
                        // Montée continue, rebouclée sous l'écran.
                        val progress = (mote.startY - time * mote.speed).mod(1.2f) - 0.1f
                        val y = progress * size.height
                        val sway = sin(time * mote.swaySpeed + mote.swayPhase) * mote.swayAmp
                        val x = (mote.x + sway) * size.width

                        // Scintillement propre à chaque grain.
                        val twinkle = (sin(time * mote.twinkleSpeed + mote.twinklePhase) + 1f) / 2f
                        val alpha = mote.alpha * (0.35f + twinkle * 0.65f)
                        val radius = mote.radius * size.width

                        // Halo large et diffus, puis cœur net : deux cercles suffisent à
                        // suggérer la braise, sans allouer un dégradé par grain et par frame.
                        drawCircle(
                            color = AURA_GOLD.copy(alpha = alpha * 0.22f),
                            radius = radius * 3.2f,
                            center = Offset(x, y)
                        )
                        drawCircle(
                            color = MOTE_CORE.copy(alpha = alpha),
                            radius = radius,
                            center = Offset(x, y)
                        )
                    }
                }
        )

        Text(
            text = "touche l'écran",
            style = MaterialTheme.typography.bodySmall,
            color = Color.White.copy(alpha = 0.35f + 0.25f * (sin(elapsed / 900f) + 1f) / 2f),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .wrapContentSize()
                .padding(bottom = 56.dp)
        )
    }
}

/**
 * Un grain d'aura. La densité est biaisée vers le bas et le centre de l'image, là où la
 * lumière peinte est la plus forte : des braises dans les coins sombres se verraient comme
 * un calque plaqué.
 */
private class Mote(
    val x: Float,
    val startY: Float,
    val speed: Float,
    val radius: Float,
    val alpha: Float,
    val swayAmp: Float,
    val swaySpeed: Float,
    val swayPhase: Float,
    val twinkleSpeed: Float,
    val twinklePhase: Float
) {
    companion object {
        fun random(): Mote {
            // Deux tirages moyennés : ramène les grains vers le centre horizontalement.
            val centred = (Random.nextFloat() + Random.nextFloat()) / 2f
            return Mote(
                x = 0.06f + centred * 0.88f,
                // Racine carrée : concentre les départs vers le bas de l'écran.
                startY = 0.35f + kotlin.math.sqrt(Random.nextFloat()) * 0.75f,
                speed = 0.012f + Random.nextFloat() * 0.050f,
                radius = 0.0016f + Random.nextFloat() * 0.0048f,
                alpha = 0.30f + Random.nextFloat() * 0.65f,
                swayAmp = 0.004f + Random.nextFloat() * 0.022f,
                swaySpeed = 0.3f + Random.nextFloat() * 0.9f,
                swayPhase = Random.nextFloat() * 6.283f,
                twinkleSpeed = 1.1f + Random.nextFloat() * 3.4f,
                twinklePhase = Random.nextFloat() * 6.283f
            )
        }
    }
}

private const val MOTE_COUNT = 280
private const val BREATH_SPEED = 0.55f
private val AURA_GOLD = Color(0xFFFFC24A)
private val MOTE_CORE = Color(0xFFFFF0C0)
