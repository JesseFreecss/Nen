package com.jesse.gardefou.pomodoro

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.drawable.Icon
import android.os.Build
import android.os.IBinder
import com.jesse.gardefou.MainActivity
import com.jesse.gardefou.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Minuteur Pomodoro, tenu par un service de premier plan.
 *
 * C'est tout l'intérêt du service : le décompte doit continuer quand l'utilisateur quitte Nen
 * pour une autre app. Un minuteur porté par l'écran Compose serait suspendu dès la mise en
 * arrière-plan. La notification persistante est la contrepartie imposée par Android, et fait
 * aussi office de télécommande (pause, phase suivante, arrêt).
 *
 * Le service ne « tourne » pas à la seconde : chaque phase est une simple attente jusqu'à son
 * instant de fin, et la notification affiche le décompte elle-même (chronomètre à rebours).
 */
class PomodoroService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var phaseJob: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createChannels()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Le système exige startForeground() dans les secondes qui suivent
        // startForegroundService(), quelle que soit l'action reçue — y compris un arrêt.
        goForeground()

        when (intent?.action) {
            ACTION_START -> {
                val work = intent.getIntExtra(EXTRA_WORK_MINUTES, DEFAULT_WORK_MINUTES)
                val rest = intent.getIntExtra(EXTRA_BREAK_MINUTES, DEFAULT_BREAK_MINUTES)
                PomodoroStateHolder.update {
                    it.copy(workMinutes = work, breakMinutes = rest, roundsDone = 0)
                }
                beginPhase(PomodoroPhase.WORK)
            }
            ACTION_PAUSE -> pause()
            ACTION_RESUME -> resume()
            ACTION_SKIP -> nextPhase()
            // Arrêt demandé, ou redémarrage du service par le système : dans ce dernier cas
            // l'intent est null et l'état du cycle est perdu, il n'y a rien à reprendre.
            else -> stopEverything()
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        phaseJob?.cancel()
        scope.cancel()
        // Le service peut aussi être tué par le système : sans cette remise à plat, l'écran
        // continuerait d'afficher un cycle en cours que plus personne ne décompte.
        PomodoroStateHolder.update {
            PomodoroState(workMinutes = it.workMinutes, breakMinutes = it.breakMinutes)
        }
    }

    // --- Cycle ---

    private fun beginPhase(phase: PomodoroPhase) {
        val state = PomodoroStateHolder.state.value
        val minutes = if (phase == PomodoroPhase.WORK) state.workMinutes else state.breakMinutes
        val durationMs = minutes * 60_000L
        PomodoroStateHolder.update {
            it.copy(
                phase = phase,
                endAtMs = System.currentTimeMillis() + durationMs,
                remainingMs = durationMs,
                paused = false
            )
        }
        goForeground()
        schedulePhaseEnd()
    }

    private fun schedulePhaseEnd() {
        phaseJob?.cancel()
        phaseJob = scope.launch {
            val wait = PomodoroStateHolder.state.value.endAtMs - System.currentTimeMillis()
            if (wait > 0) delay(wait)
            alertPhaseEnded(PomodoroStateHolder.state.value.phase)
            nextPhase()
        }
    }

    private fun nextPhase() {
        val state = PomodoroStateHolder.state.value
        if (!state.running) return
        if (state.phase == PomodoroPhase.WORK) {
            PomodoroStateHolder.update { it.copy(roundsDone = it.roundsDone + 1) }
            beginPhase(PomodoroPhase.BREAK)
        } else {
            beginPhase(PomodoroPhase.WORK)
        }
    }

    private fun pause() {
        val state = PomodoroStateHolder.state.value
        if (!state.running || state.paused) return
        phaseJob?.cancel()
        PomodoroStateHolder.update {
            it.copy(paused = true, remainingMs = it.remainingAt(System.currentTimeMillis()))
        }
        goForeground()
    }

    private fun resume() {
        val state = PomodoroStateHolder.state.value
        if (!state.running || !state.paused) return
        PomodoroStateHolder.update {
            it.copy(paused = false, endAtMs = System.currentTimeMillis() + it.remainingMs)
        }
        goForeground()
        schedulePhaseEnd()
    }

    private fun stopEverything() {
        phaseJob?.cancel()
        PomodoroStateHolder.update {
            PomodoroState(workMinutes = it.workMinutes, breakMinutes = it.breakMinutes)
        }
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    // --- Notifications ---

    private fun createChannels() {
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                "Pomodoro",
                NotificationManager.IMPORTANCE_LOW
            ).apply { description = "Décompte de la phase en cours" }
        )
        // Canal distinct, sonore : le changement de phase est justement ce qu'il faut
        // remarquer quand on est dans une autre app.
        manager.createNotificationChannel(
            NotificationChannel(
                ALERT_CHANNEL_ID,
                "Pomodoro — fin de phase",
                NotificationManager.IMPORTANCE_HIGH
            ).apply { description = "Signale le passage travail / pause" }
        )
    }

    private fun goForeground() {
        val notification = buildNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun buildNotification(): Notification {
        val state = PomodoroStateHolder.state.value
        val label = when (state.phase) {
            PomodoroPhase.WORK -> "Travail"
            PomodoroPhase.BREAK -> "Pause"
            PomodoroPhase.IDLE -> "Pomodoro"
        }

        val builder = Notification.Builder(this, CHANNEL_ID)
            .setContentTitle("Pomodoro — $label")
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentIntent(openAppIntent())
            .setOngoing(true)
            .setOnlyAlertOnce(true)

        if (state.paused) {
            builder.setContentText("En pause — ${formatRemaining(state.remainingMs)} restant")
            builder.addAction(action("Reprendre", ACTION_RESUME, REQUEST_RESUME))
        } else {
            builder.setContentText(
                if (state.roundsDone > 0) "Cycle ${state.roundsDone + 1}" else "En cours"
            )
            // Décompte affiché par le système : pas besoin de republier la notification
            // chaque seconde.
            builder.setWhen(state.endAtMs)
                .setUsesChronometer(true)
                .setChronometerCountDown(true)
            builder.addAction(action("Pause", ACTION_PAUSE, REQUEST_PAUSE))
        }
        builder.addAction(action("Passer", ACTION_SKIP, REQUEST_SKIP))
        builder.addAction(action("Arrêter", ACTION_STOP, REQUEST_STOP))

        return builder.build()
    }

    private fun alertPhaseEnded(finished: PomodoroPhase) {
        val state = PomodoroStateHolder.state.value
        val title: String
        val text: String
        if (finished == PomodoroPhase.WORK) {
            title = "Pause"
            text = "Temps de travail écoulé. ${state.breakMinutes} min de pause."
        } else {
            title = "Au travail"
            text = "Pause terminée. ${state.workMinutes} min de concentration."
        }
        val notification = Notification.Builder(this, ALERT_CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(text)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentIntent(openAppIntent())
            .setAutoCancel(true)
            .build()
        getSystemService(NotificationManager::class.java).notify(ALERT_NOTIFICATION_ID, notification)
    }

    private fun openAppIntent(): PendingIntent = PendingIntent.getActivity(
        this, 0,
        Intent(this, MainActivity::class.java),
        PendingIntent.FLAG_IMMUTABLE
    )

    private fun action(label: String, action: String, requestCode: Int): Notification.Action {
        val intent = PendingIntent.getService(
            this, requestCode,
            Intent(this, PomodoroService::class.java).setAction(action),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        return Notification.Action.Builder(
            Icon.createWithResource(this, R.mipmap.ic_launcher),
            label,
            intent
        ).build()
    }

    companion object {
        private const val CHANNEL_ID = "gardefou_pomodoro"
        private const val ALERT_CHANNEL_ID = "gardefou_pomodoro_alert"
        private const val NOTIFICATION_ID = 2
        private const val ALERT_NOTIFICATION_ID = 3

        private const val REQUEST_PAUSE = 10
        private const val REQUEST_RESUME = 11
        private const val REQUEST_SKIP = 12
        private const val REQUEST_STOP = 13

        const val ACTION_START = "com.jesse.gardefou.pomodoro.START"
        const val ACTION_PAUSE = "com.jesse.gardefou.pomodoro.PAUSE"
        const val ACTION_RESUME = "com.jesse.gardefou.pomodoro.RESUME"
        const val ACTION_SKIP = "com.jesse.gardefou.pomodoro.SKIP"
        const val ACTION_STOP = "com.jesse.gardefou.pomodoro.STOP"

        private const val EXTRA_WORK_MINUTES = "work_minutes"
        private const val EXTRA_BREAK_MINUTES = "break_minutes"

        fun start(context: Context, workMinutes: Int, breakMinutes: Int) {
            context.startForegroundService(
                Intent(context, PomodoroService::class.java)
                    .setAction(ACTION_START)
                    .putExtra(EXTRA_WORK_MINUTES, workMinutes)
                    .putExtra(EXTRA_BREAK_MINUTES, breakMinutes)
            )
        }

        fun send(context: Context, action: String) {
            context.startForegroundService(
                Intent(context, PomodoroService::class.java).setAction(action)
            )
        }
    }
}
