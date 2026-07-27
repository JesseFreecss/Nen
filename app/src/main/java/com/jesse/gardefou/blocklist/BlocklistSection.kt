package com.jesse.gardefou.blocklist

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
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

/** Message affiché quand aucun vœu n'est scellé. */
@Composable
fun EmptyVows(modifier: Modifier = Modifier) {
    Text(
        text = "Aucun vœu scellé. Scellez-en un pour bloquer les domaines correspondants.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier
    )
}

/**
 * Les vœux, scellés sous forme d'orbes. Rien n'est lisible tant qu'une orbe n'a pas été
 * touchée et l'empreinte validée ; le vœu révélé prend alors la place de son orbe.
 *
 * L'affichage est plafonné à [MAX_ORBS] : après un import de liste hosts, la base peut
 * compter des dizaines de milliers d'entrées, et autant d'orbes animées seraient
 * ingérables à l'écran comme pour le processeur.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SealedVows(
    keywords: List<BlockedKeyword>,
    revealedId: Long?,
    onOrbClick: (BlockedKeyword) -> Unit,
    onUnseal: (BlockedKeyword) -> Unit,
    modifier: Modifier = Modifier
) {
    val shown = keywords.take(MAX_ORBS)

    Column(modifier = modifier.fillMaxWidth()) {
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            shown.forEachIndexed { index, vow ->
                if (vow.id == revealedId) {
                    RevealedVow(item = vow, onUnseal = { onUnseal(vow) })
                } else {
                    VowOrb(onClick = { onOrbClick(vow) }, seed = index)
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
    }
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
 * Pied de section : le bouton « Sceller un vœu » et, en dessous, l'import d'une liste au
 * format hosts. L'import est une action rare : simple bouton texte, pour ne pas concurrencer
 * visuellement le bouton principal.
 */
@Composable
fun VowsFooter(
    importing: Boolean,
    lastImportCount: Int?,
    onSeal: () -> Unit,
    onImport: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth()) {
        OutlinedButton(
            onClick = onSeal,
            shape = RoundedCornerShape(12.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = "Sceller un vœu",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.padding(vertical = 6.dp)
            )
        }

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
