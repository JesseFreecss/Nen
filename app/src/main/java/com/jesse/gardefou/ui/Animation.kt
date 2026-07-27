package com.jesse.gardefou.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.produceState
import androidx.compose.runtime.withFrameNanos

/**
 * Millisecondes écoulées depuis l'apparition du composable, mises à jour à chaque frame.
 *
 * Sert de base de temps aux animations dessinées à la main (grains d'aura, orbes). On
 * n'utilise pas `rememberInfiniteTransition` : il fait osciller une valeur entre deux bornes,
 * alors qu'il faut ici un temps qui croît sans jamais revenir en arrière — des animations de
 * vitesses différentes se remettraient sinon en phase à chaque rebouclage.
 */
@Composable
fun rememberElapsedMillis(): Float {
    val state: State<Float> = produceState(0f) {
        val start = withFrameNanos { it }
        while (true) {
            withFrameNanos { now -> value = (now - start) / 1_000_000f }
        }
    }
    return state.value
}
