package com.jesse.nen.sound

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.jesse.nen.data.AmbienceTrack

/**
 * La bibliothèque de morceaux d'ambiance : ajouter/retirer des fichiers audio et choisir
 * lequel joue, sans repasser par le sélecteur de fichiers à chaque fois qu'on veut changer.
 *
 * Un seul morceau actif à la fois (voir [AmbiencePrefs]) : taper sur un autre morceau de la
 * liste le rend actif et le lance ; retaper sur l'actif bascule juste lecture/pause.
 */
@Composable
fun AmbienceLibraryDialog(
    tracks: List<AmbienceTrack>,
    activeUri: String?,
    playing: Boolean,
    volume: Float,
    onSelect: (AmbienceTrack) -> Unit,
    onToggleActive: () -> Unit,
    onRemove: (AmbienceTrack) -> Unit,
    onAddRequested: () -> Unit,
    onVolumeChange: (Float) -> Unit,
    onDismiss: () -> Unit
) {
    // remember(volume) : resynchronise le curseur si le volume a changé hors de ce dialogue
    // (aucun cas actuel, mais évite qu'il reste figé sur une valeur périmée si ça arrivait).
    var sliderValue by remember(volume) { mutableFloatStateOf(volume) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(16.dp),
        title = {
            Text(
                text = "Musique d'ambiance",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
        },
        text = {
            Column {
                if (tracks.isEmpty()) {
                    Text(
                        text = "Aucun morceau ajouté pour l'instant.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 280.dp)
                    ) {
                        items(tracks, key = { it.id }) { track ->
                            val isActive = track.uri == activeUri
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { if (isActive) onToggleActive() else onSelect(track) }
                                    .padding(vertical = 8.dp)
                            ) {
                                Text(
                                    text = track.displayName,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = if (isActive) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.onSurface
                                    },
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f)
                                )
                                if (isActive) {
                                    Text(
                                        text = if (playing) "⏸" else "▶",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.padding(horizontal = 8.dp)
                                    )
                                }
                                TextButton(onClick = { onRemove(track) }) {
                                    Text(
                                        text = "×",
                                        style = MaterialTheme.typography.titleMedium,
                                        color = MaterialTheme.colorScheme.error
                                    )
                                }
                            }
                        }
                    }
                }

                OutlinedButton(
                    onClick = onAddRequested,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp)
                ) {
                    Text("+ Ajouter un morceau")
                }

                Text(
                    text = "Volume",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 16.dp)
                )
                Slider(
                    value = sliderValue,
                    onValueChange = {
                        sliderValue = it
                        onVolumeChange(it)
                    }
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Fermer") }
        }
    )
}
