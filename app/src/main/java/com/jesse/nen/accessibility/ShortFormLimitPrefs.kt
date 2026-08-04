package com.jesse.nen.accessibility

import android.content.Context
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Plateforme de vidéos courtes suivie par [ShortFormLimitPrefs]. */
enum class ShortFormPlatform(val keyPrefix: String) {
    INSTAGRAM_REELS("reels"),
    YOUTUBE_SHORTS("shorts")
}

/**
 * Budget quotidien de temps passé dans les Reels Instagram / Shorts YouTube, par plateforme.
 * Même moule que ProtectionPrefs/PomodoroPrefs, fichier de prefs partagé.
 *
 * Le cumul du jour se remet à zéro tout seul : aucune tâche planifiée n'est nécessaire, la
 * date stockée est simplement comparée à celle du jour à chaque lecture (cf. PomodoroPrefs
 * pour le même principe appliqué aux durées de session).
 */
object ShortFormLimitPrefs {
    private const val FILE = "nen_prefs"

    const val DEFAULT_LIMIT_MINUTES = 15
    const val MIN_LIMIT_MINUTES = 5
    const val MAX_LIMIT_MINUTES = 180

    private val DAY_FORMAT = SimpleDateFormat("yyyy-MM-dd", Locale.US)

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    fun limitMinutes(context: Context, platform: ShortFormPlatform): Int =
        prefs(context).getInt("${platform.keyPrefix}_limit_minutes", DEFAULT_LIMIT_MINUTES)

    fun setLimitMinutes(context: Context, platform: ShortFormPlatform, minutes: Int) {
        prefs(context).edit()
            .putInt(
                "${platform.keyPrefix}_limit_minutes",
                minutes.coerceIn(MIN_LIMIT_MINUTES, MAX_LIMIT_MINUTES)
            )
            .apply()
    }

    /** Temps déjà cumulé aujourd'hui. Revient à 0 tout seul dès que le jour stocké diffère. */
    fun todayAccumulatedMs(context: Context, platform: ShortFormPlatform): Long {
        val stored = prefs(context)
        if (stored.getString("${platform.keyPrefix}_day", null) != todayKey()) return 0L
        return stored.getLong("${platform.keyPrefix}_accumulated_ms", 0L)
    }

    fun addAccumulatedMs(context: Context, platform: ShortFormPlatform, deltaMs: Long) {
        if (deltaMs <= 0L) return
        val current = todayAccumulatedMs(context, platform)
        prefs(context).edit()
            .putString("${platform.keyPrefix}_day", todayKey())
            .putLong("${platform.keyPrefix}_accumulated_ms", current + deltaMs)
            .apply()
    }

    private fun todayKey(): String = DAY_FORMAT.format(Date())
}
