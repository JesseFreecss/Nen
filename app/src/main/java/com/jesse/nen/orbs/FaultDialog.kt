package com.jesse.nen.orbs

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

/**
 * Ce qui manque, pourquoi c'est un problème, et le réglage qui le corrige.
 *
 * [onMute], quand il est fourni, permet de faire taire l'alerte définitivement : la demande
 * système d'exclusion de batterie reste sans effet sur certaines surcouches, et sans cette
 * porte de sortie l'orbe rouge resterait à l'écran quoi que fasse l'utilisateur.
 */
@Composable
fun FaultDialog(
    fault: FaultKind,
    onDismiss: () -> Unit,
    onAction: () -> Unit,
    onMute: (() -> Unit)? = null
) {
    val title: String
    val message: String
    val actionLabel: String
    when (fault) {
        FaultKind.ACCESSIBILITY_OFF -> {
            title = "Faille : surveillance in-app"
            message = "Pour filtrer les URL dans les navigateurs et détecter les YouTube " +
                "Shorts, activez « Protection Nen » dans les réglages d'accessibilité."
            actionLabel = "Ouvrir les réglages"
        }
        FaultKind.ACCESSIBILITY_DEAD -> {
            title = "Faille : surveillance interrompue"
            message = "« Protection Nen » est activée dans les réglages, mais le service ne " +
                "répond plus : le système l'a arrêté. Rien n'est surveillé pour le moment. " +
                "Désactivez puis réactivez-la pour la relancer."
            actionLabel = "Ouvrir les réglages"
        }
        FaultKind.BATTERY -> {
            title = "Faille : batterie"
            message = "Sans exclusion des optimisations de batterie, le système peut arrêter " +
                "Nen en arrière-plan et la protection se coupe toute seule.\n\n" +
                "Sur Xiaomi, le bouton ci-dessous n'ouvre pas la demande d'autorisation " +
                "d'Android : HyperOS le détourne vers son propre écran « Détails de la " +
                "batterie », qui ne peut pas accorder cette exclusion. Il n'y a alors rien à " +
                "y faire de plus.\n\n" +
                "Ce qui protège réellement Nen sur cet appareil : « Pas de restriction » dans " +
                "cet écran Xiaomi, et le « démarrage automatique » autorisé. Une fois les " +
                "deux faits, faites taire cette alerte — Android continuera de la signaler " +
                "sans jamais pouvoir être satisfait."
            actionLabel = "Ouvrir les réglages batterie"
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(16.dp),
        title = {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.error
            )
        },
        text = {
            Column {
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (onMute != null) {
                    TextButton(onClick = onMute) {
                        Text(
                            text = "Ne plus signaler",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onAction) { Text(actionLabel) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Plus tard", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    )
}
