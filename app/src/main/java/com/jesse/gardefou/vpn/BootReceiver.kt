package com.jesse.gardefou.vpn

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.VpnService
import android.util.Log

/**
 * Relance la protection au démarrage du téléphone, si l'utilisateur l'avait laissée active.
 *
 * C'est le filet de sécurité principal : quand le système tue le service (Doze, économiseur
 * de batterie, gestionnaire d'énergie du constructeur), la protection revient au plus tard
 * au redémarrage suivant, sans intervention.
 *
 * Le service est démarré tout de suite, mais il n'établit le tunnel qu'après un délai
 * (voir BOOT_START_DELAY_MS) : pendant l'initialisation du système, un composant privilégié
 * du constructeur (sur Xiaomi, SecurityCenter détient CONTROL_VPN) appelle `prepareVpn()`,
 * ce qui réinitialise un tunnel fraîchement monté.
 *
 * Attention (Xiaomi/MIUI, Samsung...) : ce receiver n'est appelé que si le « démarrage
 * automatique » (autostart) est autorisé pour l'app dans les réglages du constructeur.
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        if (!ProtectionPrefs.isEnabled(context)) return

        if (VpnService.prepare(context) != null) {
            Log.w(TAG, "Autorisation VPN absente au démarrage : relance impossible")
            return
        }

        Log.i(TAG, "Redémarrage détecté, relance de la protection")
        GardeFouVpnService.start(context, GardeFouVpnService.BOOT_START_DELAY_MS)
    }

    private companion object {
        const val TAG = "GardeFouBoot"
    }
}
