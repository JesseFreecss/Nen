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
    private const val KEY_ENABLED_SINCE = "protection_enabled_since"

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    fun setEnabled(context: Context, enabled: Boolean) {
        val prefs = prefs(context)
        val editor = prefs.edit().putBoolean(KEY_ENABLED, enabled)
        if (enabled) {
            // Le service rappelle setEnabled(true) à CHAQUE établissement du tunnel : relance
            // après un kill système, redémarrage du téléphone… On n'horodate donc que si la
            // série n'a pas déjà un début, sinon une coupure subie la remettrait à zéro.
            if (prefs.getLong(KEY_ENABLED_SINCE, 0L) == 0L) {
                editor.putLong(KEY_ENABLED_SINCE, System.currentTimeMillis())
            }
        } else {
            editor.remove(KEY_ENABLED_SINCE)
        }
        editor.apply()
    }

    fun isEnabled(context: Context): Boolean =
        prefs(context).getBoolean(KEY_ENABLED, false)

    /**
     * Instant où l'utilisateur a activé la protection, ou 0 s'il l'a arrêtée.
     * Sert au compteur de série de l'écran d'accueil.
     */
    fun enabledSince(context: Context): Long =
        prefs(context).getLong(KEY_ENABLED_SINCE, 0L)
}
