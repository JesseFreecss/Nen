package com.jesse.nen.accessibility

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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

/**
 * Réglage des budgets quotidiens de Reels Instagram et de Shorts YouTube, ouvert au toucher
 * de l'orbe noire. Sur le modèle de SetupDialog du Pomodoro : deux réglages en minutes,
 * enregistrés à la validation seulement.
 */
@Composable
fun ShortFormDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current

    var reelsMinutes by remember {
        mutableIntStateOf(ShortFormLimitPrefs.limitMinutes(context, ShortFormPlatform.INSTAGRAM_REELS))
    }
    var shortsMinutes by remember {
        mutableIntStateOf(ShortFormLimitPrefs.limitMinutes(context, ShortFormPlatform.YOUTUBE_SHORTS))
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(16.dp),
        title = {
            Text(
                text = "Vidéos courtes",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
        },
        text = {
            Column {
                MinutesRow(
                    label = "Reels Instagram",
                    minutes = reelsMinutes,
                    step = 5,
                    range = ShortFormLimitPrefs.MIN_LIMIT_MINUTES..ShortFormLimitPrefs.MAX_LIMIT_MINUTES,
                    onChange = { reelsMinutes = it }
                )
                MinutesRow(
                    label = "Shorts YouTube",
                    minutes = shortsMinutes,
                    step = 5,
                    range = ShortFormLimitPrefs.MIN_LIMIT_MINUTES..ShortFormLimitPrefs.MAX_LIMIT_MINUTES,
                    onChange = { shortsMinutes = it },
                    modifier = Modifier.padding(top = 8.dp)
                )
                Text(
                    text = "Budget par jour, séparément pour chaque plateforme. Une fois " +
                        "dépassé, Nen referme le flux ; le compteur repart de zéro le " +
                        "lendemain.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 16.dp)
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                ShortFormLimitPrefs.setLimitMinutes(context, ShortFormPlatform.INSTAGRAM_REELS, reelsMinutes)
                ShortFormLimitPrefs.setLimitMinutes(context, ShortFormPlatform.YOUTUBE_SHORTS, shortsMinutes)
                onDismiss()
            }) {
                Text("Enregistrer")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Fermer", color = MaterialTheme.colorScheme.onSurfaceVariant)
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
