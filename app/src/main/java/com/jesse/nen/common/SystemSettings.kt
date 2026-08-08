package com.jesse.nen.common

import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings
import android.widget.Toast
import com.jesse.nen.accessibility.NenAccessibilityService

/**
 * Indique si le service d'accessibilité de Nen est actuellement activé par l'utilisateur.
 * On lit la liste système des services activés (Settings.Secure) et on y cherche notre composant.
 */
internal fun isAccessibilityServiceEnabled(context: Context): Boolean {
    val expected = ComponentName(context, NenAccessibilityService::class.java)
    val enabledServices = Settings.Secure.getString(
        context.contentResolver,
        Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
    ) ?: return false
    return enabledServices.split(':').any { entry ->
        ComponentName.unflattenFromString(entry) == expected
    }
}

/** Indique si l'app est déjà exclue des optimisations de batterie. */
internal fun isIgnoringBatteryOptimizations(context: Context): Boolean {
    val pm = context.getSystemService(PowerManager::class.java) ?: return false
    return pm.isIgnoringBatteryOptimizations(context.packageName)
}

/**
 * Ouvre la demande système d'exclusion des optimisations de batterie.
 *
 * ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS affiche directement la boîte de dialogue
 * « Autoriser ? », mais certaines surcouches ne l'implémentent pas : on se replie alors sur
 * la liste complète des apps, où l'utilisateur choisit Nen à la main.
 */
internal fun requestIgnoreBatteryOptimizations(context: Context) {
    val direct = Intent(
        Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
        Uri.parse("package:${context.packageName}")
    ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    try {
        context.startActivity(direct)
    } catch (e: ActivityNotFoundException) {
        try {
            context.startActivity(
                Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        } catch (e2: ActivityNotFoundException) {
            Toast.makeText(
                context,
                "Réglage introuvable : ouvrez Réglages > Batterie et retirez la restriction "
                    + "pour Nen.",
                Toast.LENGTH_LONG
            ).show()
        }
    }
}
