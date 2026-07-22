package com.jesse.gardefou.blocklist

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.jesse.gardefou.data.BlockedKeyword

/**
 * Section d'UI (Composable) pour gérer la liste de mots-clés bloqués :
 * champ de saisie + bouton "Ajouter", et la liste avec un bouton "Suppr." par ligne.
 *
 * Le ViewModel est fourni automatiquement par `viewModel()`.
 */
@Composable
fun BlocklistSection(
    modifier: Modifier = Modifier,
    viewModel: KeywordViewModel = viewModel()
) {
    // Liste observée depuis le ViewModel (se met à jour toute seule à chaque changement).
    val keywords by viewModel.keywords.collectAsStateWithLifecycle()

    // État local du champ de saisie.
    var input by remember { mutableStateOf("") }

    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = "Mots-clés bloqués",
            style = MaterialTheme.typography.titleMedium
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(
                value = input,
                onValueChange = { input = it },
                singleLine = true,
                label = { Text("ex. youtube") },
                modifier = Modifier.weight(1f)
            )
            Button(
                onClick = {
                    viewModel.add(input)
                    input = ""
                }
            ) {
                Text("Ajouter")
            }
        }

        if (keywords.isEmpty()) {
            Text(
                text = "Aucun mot-clé. Ajoutez-en un pour bloquer les domaines correspondants.",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 12.dp)
            )
        } else {
            LazyColumn(modifier = Modifier.padding(top = 8.dp)) {
                items(keywords, key = { it.id }) { item ->
                    KeywordRow(item = item, onDelete = { viewModel.remove(item) })
                    HorizontalDivider()
                }
            }
        }
    }
}

@Composable
private fun KeywordRow(item: BlockedKeyword, onDelete: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = item.keyword, style = MaterialTheme.typography.bodyLarge)
        TextButton(onClick = onDelete) { Text("Suppr.") }
    }
}
