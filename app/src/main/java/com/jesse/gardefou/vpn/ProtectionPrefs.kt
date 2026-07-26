package com.jesse.gardefou.vpn

import android.content.Context

/**
 * Mémorise l'INTENTION de l'utilisateur : « la protection doit-elle être active ? ».
 *
 * À distinguer de [VpnStateHolder], qui reflète l'état réel du service en cours d'exécution.
 * L'intention survit à la mort du process : c'est elle qui permet de relancer le VPN après
 * un redémarrage du téléphone (voir [BootReceiver]).
 */
object ProtectionPrefs {
    private const val FILE = "gardefou_prefs"
    private const val KEY_ENABLED = "protection_enabled"

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    fun setEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_ENABLED, enabled).apply()
    }

    fun isEnabled(context: Context): Boolean =
        prefs(context).getBoolean(KEY_ENABLED, false)
}
