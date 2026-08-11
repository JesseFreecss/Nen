package com.jesse.nen.pomodoro

import android.content.Context
import com.jesse.nen.common.NenPrefs
import com.jesse.nen.common.SimpleStateHolder
import kotlinx.coroutines.flow.StateFlow

/** Phase du cycle. [IDLE] = aucun pomodoro en cours. */
enum class PomodoroPhase { IDLE, WORK, BREAK }

/**
 * État du minuteur, tel que le tient [PomodoroService].
 *
 * Les phases sont datées par un instant de fin ([endAtMs]) plutôt que par un compteur qu'on
 * décrémenterait : l'écran peut être éteint, l'app en arrière-plan, le décompte reste juste
 * sans que personne n'ait à « tenir » l'horloge.
 */
data class PomodoroState(
    val phase: PomodoroPhase = PomodoroPhase.IDLE,
    val endAtMs: Long = 0L,
    /** Temps figé pendant une pause manuelle ; sans objet quand ça tourne. */
    val remainingMs: Long = 0L,
    val paused: Boolean = false,
    val workMinutes: Int = DEFAULT_WORK_MINUTES,
    val breakMinutes: Int = DEFAULT_BREAK_MINUTES,
    /** Nombre de phases de travail achevées depuis le démarrage. */
    val roundsDone: Int = 0
) {
    val running: Boolean get() = phase != PomodoroPhase.IDLE

    fun remainingAt(nowMs: Long): Long =
        if (paused) remainingMs else (endAtMs - nowMs).coerceAtLeast(0L)
}

/** État partagé entre le service (qui l'écrit) et l'UI (qui l'observe). */
object PomodoroStateHolder {
    private val holder = SimpleStateHolder(PomodoroState())
    val state: StateFlow<PomodoroState> = holder.flow

    internal fun update(transform: (PomodoroState) -> PomodoroState) = holder.update(transform)
}

/** Durées choisies par l'utilisateur, conservées d'une session à l'autre. */
object PomodoroPrefs {
    private const val KEY_WORK = "pomodoro_work_minutes"
    private const val KEY_BREAK = "pomodoro_break_minutes"

    private fun prefs(context: Context) = NenPrefs.raw(context)

    fun workMinutes(context: Context): Int = prefs(context).getInt(KEY_WORK, DEFAULT_WORK_MINUTES)

    fun breakMinutes(context: Context): Int = prefs(context).getInt(KEY_BREAK, DEFAULT_BREAK_MINUTES)

    fun save(context: Context, workMinutes: Int, breakMinutes: Int) {
        prefs(context).edit()
            .putInt(KEY_WORK, workMinutes)
            .putInt(KEY_BREAK, breakMinutes)
            .apply()
    }
}

/** « 24:05 », arrondi à la seconde supérieure pour ne pas afficher 00:00 une seconde trop tôt. */
fun formatRemaining(ms: Long): String {
    val totalSeconds = (ms.coerceAtLeast(0L) + 999L) / 1000L
    return "%d:%02d".format(totalSeconds / 60, totalSeconds % 60)
}

const val DEFAULT_WORK_MINUTES = 25
const val DEFAULT_BREAK_MINUTES = 5

const val MIN_WORK_MINUTES = 5
const val MAX_WORK_MINUTES = 120
const val MIN_BREAK_MINUTES = 1
const val MAX_BREAK_MINUTES = 60
