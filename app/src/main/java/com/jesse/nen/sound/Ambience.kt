package com.jesse.nen.sound

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Le morceau d'ambiance choisi par l'utilisateur, et son volume.
 *
 * On garde une URI vers un fichier de l'appareil plutôt qu'un son embarqué dans l'app :
 * changer de morceau ne demande alors pas de recompiler, et Nen n'a pas à embarquer de
 * musique dont il n'aurait pas les droits.
 */
object AmbiencePrefs {
    private const val FILE = "nen_prefs"
    private const val KEY_URI = "ambience_uri"
    private const val KEY_VOLUME = "ambience_volume"

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    fun trackUri(context: Context): Uri? =
        prefs(context).getString(KEY_URI, null)?.let(Uri::parse)

    fun setTrackUri(context: Context, uri: Uri?) {
        val editor = prefs(context).edit()
        if (uri == null) editor.remove(KEY_URI) else editor.putString(KEY_URI, uri.toString())
        editor.apply()
    }

    fun volume(context: Context): Float = prefs(context).getFloat(KEY_VOLUME, 0.6f)

    fun setVolume(context: Context, volume: Float) {
        prefs(context).edit().putFloat(KEY_VOLUME, volume.coerceIn(0f, 1f)).apply()
    }
}

/** Écrit par [AmbienceService], observé par l'écran pour animer l'orbe. */
object AmbienceStateHolder {
    private val _playing = MutableStateFlow(false)
    val playing: StateFlow<Boolean> = _playing.asStateFlow()

    internal fun setPlaying(playing: Boolean) {
        _playing.value = playing
    }
}
