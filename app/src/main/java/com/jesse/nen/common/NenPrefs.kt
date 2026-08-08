package com.jesse.nen.common

import android.content.Context
import android.content.SharedPreferences

/**
 * Fichier de préférences partagé par [com.jesse.nen.vpn.ProtectionPrefs],
 * [com.jesse.nen.pomodoro.PomodoroPrefs], [com.jesse.nen.sound.AmbiencePrefs] et
 * [com.jesse.nen.accessibility.ShortFormLimitPrefs] — factorise l'accesseur identique que ces
 * quatre objets redéfinissaient chacun de leur côté.
 *
 * [com.jesse.nen.accessibility.A11yHeartbeat] utilise volontairement un fichier séparé
 * (`nen_a11y`) et n'est pas concerné par cette factorisation.
 */
internal object NenPrefs {
    // NE PAS renommer : c'est le nom du fichier SharedPreferences déjà présent sur les
    // appareils existants. Le changer viderait silencieusement toutes les préférences.
    private const val FILE = "nen_prefs"

    fun raw(context: Context): SharedPreferences =
        context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)
}
