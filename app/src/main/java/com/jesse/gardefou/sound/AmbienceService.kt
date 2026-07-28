package com.jesse.gardefou.sound

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.drawable.Icon
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.os.Build
import android.os.IBinder
import android.util.Log
import android.widget.Toast
import com.jesse.gardefou.MainActivity
import com.jesse.gardefou.R

/**
 * Joue en boucle le morceau d'ambiance choisi par l'utilisateur.
 *
 * Service de premier plan, comme le Pomodoro et pour la même raison : l'ambiance doit tenir
 * quand on quitte Nen. Sa notification sert aussi d'arrêt d'urgence.
 *
 * Pas de gestion du focus audio : c'est une ambiance, elle est faite pour se superposer à ce
 * que fait le téléphone plutôt que pour en prendre la main.
 */
class AmbienceService : Service() {

    private var player: MediaPlayer? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                "Ambiance",
                NotificationManager.IMPORTANCE_LOW
            ).apply { description = "Musique d'ambiance en cours" }
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Le système exige startForeground() dans les secondes qui suivent
        // startForegroundService(), quelle que soit l'action reçue.
        goForeground()

        when (intent?.action) {
            ACTION_PLAY -> play()
            ACTION_SET_VOLUME -> {
                val volume = AmbiencePrefs.volume(this)
                player?.setVolume(volume, volume)
                if (player == null) stopEverything()
            }
            // Arrêt demandé, ou redémarrage du service par le système sans intent.
            else -> stopEverything()
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        release()
    }

    private fun play() {
        val uri = AmbiencePrefs.trackUri(this)
        if (uri == null) {
            stopEverything()
            return
        }
        release()

        val volume = AmbiencePrefs.volume(this)
        try {
            player = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build()
                )
                setDataSource(this@AmbienceService, uri)
                isLooping = true
                setVolume(volume, volume)
                setOnErrorListener { _, what, extra ->
                    Log.w(TAG, "Lecture interrompue ($what/$extra)")
                    stopEverything()
                    true
                }
                prepare()
                start()
            }
            AmbienceStateHolder.setPlaying(true)
        } catch (e: Exception) {
            // Fichier déplacé, supprimé, ou autorisation d'accès perdue : on oublie le
            // morceau, sinon l'orbe resterait à échouer silencieusement à chaque toucher.
            Log.w(TAG, "Morceau illisible : ${e.message}")
            AmbiencePrefs.setTrackUri(this, null)
            Toast.makeText(this, "Morceau illisible, choisissez-en un autre", Toast.LENGTH_LONG)
                .show()
            stopEverything()
        }
    }

    private fun release() {
        player?.let { current ->
            try {
                current.stop()
            } catch (_: IllegalStateException) {
            }
            current.release()
        }
        player = null
        AmbienceStateHolder.setPlaying(false)
    }

    private fun stopEverything() {
        release()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun goForeground() {
        val notification = Notification.Builder(this, CHANNEL_ID)
            .setContentTitle("Ambiance")
            .setContentText("Lecture en boucle")
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentIntent(
                PendingIntent.getActivity(
                    this, 0,
                    Intent(this, MainActivity::class.java),
                    PendingIntent.FLAG_IMMUTABLE
                )
            )
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .addAction(
                Notification.Action.Builder(
                    Icon.createWithResource(this, R.mipmap.ic_launcher),
                    "Arrêter",
                    PendingIntent.getService(
                        this, 1,
                        Intent(this, AmbienceService::class.java).setAction(ACTION_STOP),
                        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                    )
                ).build()
            )
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    companion object {
        private const val TAG = "NenAmbience"
        private const val CHANNEL_ID = "gardefou_ambience"
        private const val NOTIFICATION_ID = 4

        const val ACTION_PLAY = "com.jesse.gardefou.sound.PLAY"
        const val ACTION_STOP = "com.jesse.gardefou.sound.STOP"
        const val ACTION_SET_VOLUME = "com.jesse.gardefou.sound.SET_VOLUME"

        fun send(context: Context, action: String) {
            context.startForegroundService(
                Intent(context, AmbienceService::class.java).setAction(action)
            )
        }
    }
}
