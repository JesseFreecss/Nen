package com.jesse.nen.accessibility

import android.content.Context
import android.os.SystemClock

/**
 * Suivi de session pour UNE plateforme de vidéos courtes (Reels ou Shorts), au nom de
 * [NenAccessibilityService] : démarre/prolonge la session en cours et reverse
 * périodiquement le temps écoulé au cumul persisté ([ShortFormLimitPrefs]), pour ne pas
 * perdre la progression du jour si le process est tué (HyperOS) avant la sortie normale
 * du flux.
 */
internal class ShortFormBudgetTracker(private val platform: ShortFormPlatform) {

    // Horodatage (uptime) du début de la session en cours, ou null hors du flux.
    @Volatile private var sessionStartMs: Long? = null

    /**
     * À appeler à chaque détection du flux actif. Démarre la session si elle ne l'est pas
     * encore, et renvoie vrai dès que le cumul du jour (déjà persisté + temps écoulé depuis
     * le début de la session) dépasse le seuil réglé pour la plateforme — le temps de la
     * session est alors reversé au cumul et une nouvelle session repart aussitôt.
     *
     * Le temps de la session n'est PAS reversé au cumul à chaque appel (trop fréquent, un
     * événement de contenu peut arriver plusieurs fois par seconde) : seuls [flush]
     * (périodique ou en sortie de flux) et un dépassement de limite le font.
     */
    fun onFlowDetected(context: Context): Boolean {
        val now = SystemClock.uptimeMillis()
        val start = sessionStartMs
        if (start == null) {
            sessionStartMs = now
            return false
        }
        val elapsedSession = now - start
        val accumulatedToday = ShortFormLimitPrefs.todayAccumulatedMs(context, platform)
        val limitMs = ShortFormLimitPrefs.limitMinutes(context, platform) * 60_000L
        if (isOverLimit(accumulatedToday, elapsedSession, limitMs)) {
            ShortFormLimitPrefs.addAccumulatedMs(context, platform, elapsedSession)
            sessionStartMs = now
            return true
        }
        return false
    }

    /**
     * Reverse le temps écoulé de la session en cours au cumul persisté du jour.
     *
     * @param endSession si vrai, referme la session (sortie du flux) ; sinon la relance
     * immédiatement à partir de maintenant (flush périodique d'une session toujours active).
     */
    fun flush(context: Context, endSession: Boolean = false) {
        val start = sessionStartMs ?: return
        val now = SystemClock.uptimeMillis()
        val elapsed = now - start
        if (elapsed > 0) ShortFormLimitPrefs.addAccumulatedMs(context, platform, elapsed)
        sessionStartMs = if (endSession) null else now
    }

    internal companion object {
        /** Le cumul du jour, augmenté du temps de la session en cours, dépasse-t-il la limite ? */
        internal fun isOverLimit(accumulatedMs: Long, elapsedMs: Long, limitMs: Long): Boolean =
            accumulatedMs + elapsedMs >= limitMs
    }
}
