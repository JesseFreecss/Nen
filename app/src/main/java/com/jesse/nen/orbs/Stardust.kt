package com.jesse.nen.orbs

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import com.jesse.nen.ui.rememberElapsedMillis
import kotlin.math.sin
import kotlin.random.Random

/** Une étoile de la traînée : sa position, sa naissance, sa taille et sa phase de scintillement. */
internal class Mote(
    val x: Float,
    val y: Float,
    val bornAtMs: Long,
    val radiusDp: Float,
    val bright: Boolean,
    val twinklePhase: Float
)

/**
 * La traînée de poussière d'étoile qui suit le doigt quand on glisse sur le fond — jamais sur
 * une orbe, ni au simple tap : [spawn] n'est appelé que depuis la branche « fond » du geste dans
 * `OrbField`.
 *
 * Chaque appel sème plusieurs petites étoiles dispersées autour du point, comme un vrai nuage
 * d'étoiles plutôt qu'un halo unique — la plupart minuscules, quelques-unes plus brillantes.
 * La liste vit hors de la recomposition Compose ; elle n'est lue qu'au dessin de chaque frame.
 */
class StardustTrail {
    private val motes = mutableListOf<Mote>()
    private val random = Random(System.nanoTime())

    fun spawn(x: Float, y: Float, nowMs: Long) {
        repeat(STARS_PER_SPAWN) {
            val jitterX = (random.nextFloat() - 0.5f) * JITTER_RADIUS_PX
            val jitterY = (random.nextFloat() - 0.5f) * JITTER_RADIUS_PX
            val bright = random.nextFloat() < 0.10f
            val radiusDp = if (bright) {
                0.9f + random.nextFloat() * 0.7f
            } else {
                0.4f + random.nextFloat() * 0.7f
            }
            motes.add(Mote(x + jitterX, y + jitterY, nowMs, radiusDp, bright, random.nextFloat() * 6.28f))
        }
        while (motes.size > MAX_MOTES) motes.removeAt(0)
    }

    /** Purge les étoiles éteintes et rend un instantané pour le dessin de cette frame. */
    internal fun snapshot(nowMs: Long): List<Mote> {
        motes.removeAll { nowMs - it.bornAtMs > LIFETIME_MS }
        return motes
    }

    companion object {
        const val LIFETIME_MS = 900L
        private const val MAX_MOTES = 600
        private const val STARS_PER_SPAWN = 3
        private const val JITTER_RADIUS_PX = 16f
    }
}

/**
 * Dessine la traînée. `rememberElapsedMillis` ne sert qu'à forcer un redessin à chaque frame —
 * l'âge réel des étoiles se calcule sur l'horloge de `System.currentTimeMillis` posée à leur
 * naissance dans `OrbField`, la seule accessible depuis le bloc de geste.
 */
@Composable
fun StardustLayer(trail: StardustTrail, modifier: Modifier = Modifier) {
    val elapsed = rememberElapsedMillis()

    Canvas(modifier = modifier.fillMaxSize()) {
        val now = System.currentTimeMillis()
        for (mote in trail.snapshot(now)) {
            val age = (now - mote.bornAtMs).toFloat() / StardustTrail.LIFETIME_MS
            val life = (1f - age).coerceIn(0f, 1f)
            val twinkle = 0.55f + 0.45f * sin(elapsed / 90f + mote.twinklePhase)
            val alpha = (life * twinkle.coerceIn(0f, 1f)).coerceIn(0f, 1f)
            val position = Offset(mote.x, mote.y)
            val r = mote.radiusDp * density

            if (mote.bright) {
                drawCircle(
                    brush = Brush.radialGradient(
                        0.00f to Color.White.copy(alpha = alpha),
                        0.35f to STAR_BLUE.copy(alpha = alpha * 0.65f),
                        1.00f to Color.Transparent,
                        center = position,
                        radius = r * 1.8f
                    ),
                    radius = r * 1.8f,
                    center = position,
                    blendMode = BlendMode.Plus
                )
                drawCircle(color = Color.White.copy(alpha = alpha), radius = r * 0.4f, center = position)
            } else {
                drawCircle(color = Color.White.copy(alpha = alpha * 0.85f), radius = r, center = position)
            }
        }
    }
}

private val STAR_BLUE = Color(0xFFBFD4FF)
