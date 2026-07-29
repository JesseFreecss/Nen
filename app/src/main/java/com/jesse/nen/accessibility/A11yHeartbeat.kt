package com.jesse.nen.accessibility

import android.content.Context

/**
 * Battement de cœur du service d'accessibilité.
 *
 * Settings.Secure dit seulement si le service est ACTIVÉ, jamais s'il tourne encore. Sur les
 * surcouches agressives (HyperOS sur Xiaomi), le système tue le processus sans toucher au
 * réglage : l'app afficherait « tout va bien » alors que plus rien n'est surveillé.
 *
 * Le service inscrit donc l'heure courante à intervalle régulier. Un battement trop ancien
 * est le seul indice fiable d'une protection morte.
 */
object A11yHeartbeat {
    private const val FILE = "nen_a11y"
    private const val KEY_LAST_BEAT = "last_beat"

    /** Intervalle entre deux battements. */
    const val BEAT_INTERVAL_MS = 60_000L

    /** Au-delà, on considère le service mort : deux battements manqués, plus une marge. */
    const val STALE_AFTER_MS = 3 * BEAT_INTERVAL_MS

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    fun beat(context: Context) {
        prefs(context).edit().putLong(KEY_LAST_BEAT, System.currentTimeMillis()).apply()
    }

    /**
     * Vrai si le service a donné signe de vie récemment. Un battement dans le futur (heure
     * système reculée) est traité comme valide : mieux vaut rater une alerte que d'en
     * inventer une.
     */
    fun isAlive(context: Context): Boolean {
        val last = prefs(context).getLong(KEY_LAST_BEAT, 0L)
        if (last == 0L) return false
        return System.currentTimeMillis() - last < STALE_AFTER_MS
    }
}
