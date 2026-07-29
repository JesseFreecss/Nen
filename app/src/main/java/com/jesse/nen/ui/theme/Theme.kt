package com.jesse.nen.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val GrimoireColorScheme = darkColorScheme(
    primary = GrimoireAura,
    onPrimary = GrimoireBackground,
    background = GrimoireBackground,
    onBackground = GrimoireTextPrimary,
    surface = GrimoireSurface,
    onSurface = GrimoireTextPrimary,
    // Material 3 utilise onSurfaceVariant pour les textes secondaires (labels, légendes).
    surfaceVariant = GrimoireSurface,
    onSurfaceVariant = GrimoireTextSecondary,
    outline = GrimoireDivider,
    outlineVariant = GrimoireDivider,
    error = GrimoireDanger,
    onError = GrimoireBackground
)

/**
 * Thème racine de l'app : englobe toute l'UI Compose (voir MainActivity).
 *
 * Ni mode clair ni couleurs dynamiques (Material You) : la maquette Grimoire impose sa palette,
 * et les couleurs dynamiques la remplaceraient par celles du fond d'écran de l'utilisateur.
 */
@Composable
fun NenTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = GrimoireColorScheme,
        typography = Typography,
        content = content
    )
}
