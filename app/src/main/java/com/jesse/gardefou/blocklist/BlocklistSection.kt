package com.jesse.gardefou.blocklist

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import com.jesse.gardefou.data.BlockedKeyword

// Briques d'UI de la section « Vœux scellés ». Elles sont assemblées par la LazyColumn de
// ProtectionScreen (MainActivity) : toute la page défile d'un bloc, comme dans la maquette.
// Une LazyColumn imbriquée dans la page ne conviendrait pas — elle se retrouverait écrasée à
// quelques dizaines de dp dès qu'une carte « Faille » s'affiche.

/** En-tête de section : intitulé et nombre de vœux scellés. */
@Composable
fun VowsHeader(count: Int, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = "VŒUX SCELLÉS",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = count.toString(),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/**
 * Zone des vœux scellés : les orbes, ou le message d'absence.
 *
 * Deux gestes, et pas de bouton : un appui long sur le fond fait apparaître « Sceller un
 * vœu », un appui long sur une orbe fait apparaître « Supprimer ». L'appui long sert de
 * confirmation implicite — sans lui, une orbe supprimée d'un doigt serait perdue sans même
 * qu'on ait vu ce qu'elle contenait.
 *
 * L'affichage est plafonné à [MAX_ORBS] : après un import de liste hosts, la base peut
 * compter des dizaines de milliers d'entrées, et autant d'orbes animées seraient
 * ingérables à l'écran comme pour le processeur.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun VowsArea(
    keywords: List<BlockedKeyword>,
    revealedId: Long?,
    onOrbClick: (BlockedKeyword) -> Unit,
    onUnseal: (BlockedKeyword) -> Unit,
    onSealRequest: () -> Unit,
    modifier: Modifier = Modifier
) {
    val shown = keywords.take(MAX_ORBS)
    val density = LocalDensity.current
    val haptics = LocalHapticFeedback.current

    // Position de l'appui long : le menu s'ouvre sous le doigt, pas dans un coin de la zone.
    var sealMenuAt by remember { mutableStateOf<DpOffset?>(null) }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 96.dp)
            .pointerInput(Unit) {
                // detectTapGestures et non combinedClickable : les orbes, enfants cliquables,
                // consomment déjà leurs propres appuis. Ne remonte ici que le fond.
                detectTapGestures(
                    onLongPress = { position ->
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        sealMenuAt = with(density) { DpOffset(position.x.toDp(), position.y.toDp()) }
                    }
                )
            }
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            if (shown.isEmpty()) {
                EmptyVows()
            } else {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    shown.forEachIndexed { index, vow ->
                        if (vow.id == revealedId) {
                            RevealedVow(item = vow, onUnseal = { onUnseal(vow) })
                        } else {
                            OrbWithMenu(
                                seed = index,
                                onClick = { onOrbClick(vow) },
                                onDelete = { onUnseal(vow) }
                            )
                        }
                    }
                }

                if (keywords.size > shown.size) {
                    Text(
                        text = "+ ${keywords.size - shown.size} autres vœux scellés",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 16.dp)
                    )
                }

                Text(
                    text = "Appui long sur le fond pour sceller, sur une orbe pour supprimer.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 16.dp)
                )
            }
        }

        DropdownMenu(
            expanded = sealMenuAt != null,
            onDismissRequest = { sealMenuAt = null },
            offset = sealMenuAt ?: DpOffset.Zero
        ) {
            DropdownMenuItem(
                text = { Text("Sceller un vœu") },
                onClick = {
                    sealMenuAt = null
                    onSealRequest()
                }
            )
        }
    }
}

/** Une orbe et son menu « Supprimer », ouvert par appui long. */
@Composable
private fun OrbWithMenu(seed: Int, onClick: () -> Unit, onDelete: () -> Unit) {
    var menuOpen by remember { mutableStateOf(false) }

    Box {
        VowOrb(
            onClick = onClick,
            onLongClick = { menuOpen = true },
            seed = seed
        )
        DropdownMenu(
            expanded = menuOpen,
            onDismissRequest = { menuOpen = false }
        ) {
            DropdownMenuItem(
                text = { Text("Supprimer", color = MaterialTheme.colorScheme.error) },
                onClick = {
                    menuOpen = false
                    onDelete()
                }
            )
        }
    }
}

/** Message affiché quand aucun vœu n'est scellé. */
@Composable
private fun EmptyVows(modifier: Modifier = Modifier) {
    Text(
        text = "Aucun vœu scellé. Appui long ici pour en sceller un.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier
    )
}

private const val MAX_ORBS = 60

/**
 * Le vœu révélé après déverrouillage : son mot-clé, et l'action de le desceller.
 * Reprend la place de l'orbe le temps de la révélation.
 */
@Composable
fun RevealedVow(item: BlockedKeyword, onUnseal: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(start = 14.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            text = item.keyword,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onBackground,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        TextButton(onClick = onUnseal) {
            Text(
                text = "Desceller",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/** Une ligne de la liste : le mot-clé, et l'action de le desceller. */
@Composable
fun VowRow(item: BlockedKeyword, onUnseal: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = item.keyword,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onBackground,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .weight(1f)
                .padding(end = 8.dp)
        )
        TextButton(onClick = onUnseal) {
            Text(
                text = "Desceller",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * Pied de section : l'import d'une liste au format hosts. Sceller un vœu à l'unité se fait
 * par appui long dans la zone des orbes ([VowsArea]), pas par un bouton.
 */
@Composable
fun VowsFooter(
    importing: Boolean,
    lastImportCount: Int?,
    onImport: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            TextButton(onClick = onImport, enabled = !importing) {
                Text(
                    text = "Importer un grimoire",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (importing) {
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "Import en cours…",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else if (lastImportCount != null) {
                Text(
                    text = "$lastImportCount vœu(x) scellé(s)",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/** Boîte de dialogue d'ajout d'un mot-clé. */
@Composable
fun SealVowDialog(onDismiss: () -> Unit, onSeal: (String) -> Unit) {
    var input by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(16.dp),
        title = {
            Text(
                text = "Sceller un vœu",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
        },
        text = {
            OutlinedTextField(
                value = input,
                onValueChange = { input = it },
                singleLine = true,
                label = { Text("ex. youtube") },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(
                    onDone = { if (input.isNotBlank()) onSeal(input) }
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            // Pas de couleur explicite : TextButton grise lui-même le libellé quand il est
            // désactivé (champ vide). La forcer donnerait un bouton vert d'apparence active.
            TextButton(onClick = { onSeal(input) }, enabled = input.isNotBlank()) {
                Text("Sceller")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Annuler", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    )
}
