package com.jesse.gardefou.orbs

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitTouchSlopOrCancellation
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventTimeoutCancellationException
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.jesse.gardefou.blocklist.VowOrb
import kotlin.math.roundToInt

/**
 * Le champ d'orbes : tout l'écran, par-dessus le fond.
 *
 * Les gestes sont captés ici, une seule fois pour toutes les orbes, et non par chaque orbe :
 * une orbe traînée doit continuer de suivre le doigt même quand celui-ci sort d'elle, ce
 * qu'un `clickable` posé sur chacune ne permettrait pas.
 *
 * Les positions sont lues dans `Modifier.offset { }` : une frame de simulation ne déclenche
 * que la phase de placement, les orbes elles-mêmes ne se recomposent pas.
 */
@Composable
fun OrbField(
    engine: OrbEngine,
    specs: List<OrbSpec>,
    tenActive: Boolean,
    pomodoroActive: Boolean,
    revealedVowId: Long?,
    revealedKeyword: String?,
    onTap: (Orb) -> Unit,
    onLongPressOrb: (Orb) -> Unit,
    onLongPressBackground: (Offset) -> Unit,
    modifier: Modifier = Modifier
) {
    val haptics = LocalHapticFeedback.current
    val density = LocalDensity.current
    val currentTap by rememberUpdatedState(onTap)
    val currentLongOrb by rememberUpdatedState(onLongPressOrb)
    val currentLongBackground by rememberUpdatedState(onLongPressBackground)

    var fieldSize by remember { mutableStateOf(IntSize.Zero) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .onSizeChanged {
                fieldSize = it
                engine.setBounds(it.width.toFloat(), it.height.toFloat())
            }
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val orb = engine.orbAt(down.position)
                    val tracker = VelocityTracker()
                    tracker.addPosition(down.uptimeMillis, down.position)

                    // Deux issues au départ : le doigt franchit le seuil de glissement (on
                    // attrape l'orbe), ou il reste immobile assez longtemps pour que le geste
                    // devienne un appui long. La vibration prévient du basculement — glisser
                    // après elle traîne quand même l'orbe.
                    var longHeld = false
                    val dragStart = try {
                        withTimeout(viewConfiguration.longPressTimeoutMillis) {
                            awaitTouchSlopOrCancellation(down.id) { change, _ -> change.consume() }
                        }
                    } catch (_: PointerEventTimeoutCancellationException) {
                        longHeld = true
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        awaitTouchSlopOrCancellation(down.id) { change, _ -> change.consume() }
                    }

                    if (dragStart == null) {
                        // Doigt relâché sans avoir bougé.
                        when {
                            longHeld && orb != null -> currentLongOrb(orb)
                            longHeld -> currentLongBackground(down.position)
                            orb != null -> currentTap(orb)
                        }
                        return@awaitEachGesture
                    }

                    if (orb == null) return@awaitEachGesture

                    // L'orbe garde la prise là où le doigt l'a saisie, au lieu de sauter pour
                    // se centrer dessous.
                    val grip = Offset(down.position.x - orb.x, down.position.y - orb.y)
                    engine.grab(orb)
                    var lastTime = dragStart.uptimeMillis
                    tracker.addPosition(dragStart.uptimeMillis, dragStart.position)
                    engine.dragTo(orb, dragStart.position - grip, 0f)

                    drag(dragStart.id) { change ->
                        tracker.addPosition(change.uptimeMillis, change.position)
                        val dt = (change.uptimeMillis - lastTime).coerceAtLeast(1L) / 1000f
                        lastTime = change.uptimeMillis
                        engine.dragTo(orb, change.position - grip, dt)
                        change.consume()
                    }

                    val velocity = tracker.calculateVelocity()
                    engine.release(orb, velocity.x, velocity.y)
                }
            }
    ) {
        LaunchedEffect(specs, fieldSize) {
            if (fieldSize != IntSize.Zero) engine.sync(specs)
        }

        // Une frame de simulation par frame d'affichage.
        LaunchedEffect(engine) {
            var previous = withFrameNanos { it }
            while (true) {
                withFrameNanos { now ->
                    val dt = (now - previous) / 1_000_000_000f
                    previous = now
                    engine.step(dt, now / 1_000_000_000f)
                }
            }
        }

        engine.orbs.forEach { orb ->
            key(orb.key) {
                val kind = orb.kind
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.offset {
                        IntOffset(
                            (orb.x - orb.boxSize / 2f).roundToInt(),
                            (orb.y - orb.boxSize / 2f).roundToInt()
                        )
                    }
                ) {
                    // Le diamètre vient du moteur, jamais d'une valeur par défaut : le dessin
                    // et le corps physique doivent occuper exactement la même place.
                    val diameter = with(density) { orb.boxSize.toDp() }
                    when (kind) {
                        is OrbKind.Ten -> TenOrb(active = tenActive, diameter = diameter)
                        is OrbKind.Pomodoro ->
                            PomodoroOrb(active = pomodoroActive, diameter = diameter)
                        is OrbKind.Fault -> FaultOrb(diameter = diameter)
                        is OrbKind.Vow -> VowOrb(diameter = diameter, seed = orb.seed)
                    }

                    // Le vœu révélé s'écrit sous son orbe, le temps de la révélation.
                    if (kind is OrbKind.Vow && kind.id == revealedVowId && revealedKeyword != null) {
                        Text(
                            text = revealedKeyword,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onBackground,
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color.Black.copy(alpha = 0.65f))
                                .padding(horizontal = 8.dp, vertical = 2.dp)
                        )
                    }
                }
            }
        }
    }
}
