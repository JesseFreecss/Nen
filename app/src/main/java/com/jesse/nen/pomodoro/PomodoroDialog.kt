package com.jesse.nen.pomodoro

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

/**
 * Panneau du Pomodoro, ouvert au toucher de l'orbe blanche.
 *
 * Il ne tient aucun minuteur : il affiche l'état de [PomodoroService] et lui envoie des
 * ordres. Le fermer n'arrête donc rien — c'est le principe, le cycle doit survivre au
 * passage dans une autre app.
 */
@Composable
fun PomodoroDialog(
    state: PomodoroState,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current

    if (state.running) {
        RunningDialog(
            state = state,
            onPause = { PomodoroService.send(context, PomodoroService.ACTION_PAUSE) },
            onResume = { PomodoroService.send(context, PomodoroService.ACTION_RESUME) },
            onSkip = { PomodoroService.send(context, PomodoroService.ACTION_SKIP) },
            onStop = {
                PomodoroService.send(context, PomodoroService.ACTION_STOP)
                onDismiss()
            },
            onDismiss = onDismiss
        )
    } else {
        SetupDialog(
            initialWork = PomodoroPrefs.workMinutes(context),
            initialBreak = PomodoroPrefs.breakMinutes(context),
            onStart = { work, rest ->
                PomodoroPrefs.save(context, work, rest)
                PomodoroService.start(context, work, rest)
                onDismiss()
            },
            onDismiss = onDismiss
        )
    }
}

/** Réglage des durées, avant lancement. */
@Composable
private fun SetupDialog(
    initialWork: Int,
    initialBreak: Int,
    onStart: (Int, Int) -> Unit,
    onDismiss: () -> Unit
) {
    var work by remember { mutableIntStateOf(initialWork) }
    var rest by remember { mutableIntStateOf(initialBreak) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(16.dp),
        title = {
            Text(
                text = "Pomodoro",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
        },
        text = {
            Column {
                MinutesRow(
                    label = "Travail",
                    minutes = work,
                    step = 5,
                    range = MIN_WORK_MINUTES..MAX_WORK_MINUTES,
                    onChange = { work = it }
                )
                MinutesRow(
                    label = "Pause",
                    minutes = rest,
                    step = 1,
                    range = MIN_BREAK_MINUTES..MAX_BREAK_MINUTES,
                    onChange = { rest = it },
                    modifier = Modifier.padding(top = 8.dp)
                )
                Text(
                    text = "Les phases s'enchaînent toutes seules. Le décompte continue dans "
                        + "les autres applications, et se pilote depuis la notification.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 16.dp)
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onStart(work, rest) }) {
                Text("Démarrer")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Fermer", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    )
}

/** Cycle en cours : décompte et commandes. */
@Composable
private fun RunningDialog(
    state: PomodoroState,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onSkip: () -> Unit,
    onStop: () -> Unit,
    onDismiss: () -> Unit
) {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(state.paused, state.endAtMs) {
        while (!state.paused) {
            now = System.currentTimeMillis()
            delay(250)
        }
    }

    val phaseLabel = if (state.phase == PomodoroPhase.WORK) "Travail" else "Pause"

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(16.dp),
        title = {
            Text(
                text = phaseLabel + if (state.paused) " (en pause)" else "",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
        },
        text = {
            Column {
                Text(
                    text = formatRemaining(state.remainingAt(now)),
                    style = MaterialTheme.typography.displayMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "Cycle ${state.roundsDone + 1} · ${state.workMinutes} min de "
                        + "travail, ${state.breakMinutes} min de pause",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp)
                )
                Row(
                    modifier = Modifier.padding(top = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    TextButton(onClick = if (state.paused) onResume else onPause) {
                        Text(if (state.paused) "Reprendre" else "Pause")
                    }
                    TextButton(onClick = onSkip) {
                        Text("Passer")
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Fermer")
            }
        },
        dismissButton = {
            TextButton(onClick = onStop) {
                Text("Arrêter", color = MaterialTheme.colorScheme.error)
            }
        }
    )
}

/** Ligne de réglage : intitulé, − / +, valeur en minutes. */
@Composable
private fun MinutesRow(
    label: String,
    minutes: Int,
    step: Int,
    range: IntRange,
    onChange: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(
                onClick = { onChange((minutes - step).coerceIn(range)) },
                enabled = minutes > range.first
            ) {
                Text("−", style = MaterialTheme.typography.bodyLarge)
            }
            Text(
                text = "$minutes min",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            TextButton(
                onClick = { onChange((minutes + step).coerceIn(range)) },
                enabled = minutes < range.last
            ) {
                Text("+", style = MaterialTheme.typography.bodyLarge)
            }
        }
    }
}
