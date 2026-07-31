package com.jesse.nen.sound

import android.app.Application
import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.jesse.nen.data.AmbienceTrack
import com.jesse.nen.data.AmbienceTrackRepository
import com.jesse.nen.data.NenDatabase
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * ViewModel de la bibliothèque de morceaux d'ambiance : expose la liste à l'UI et gère
 * l'ajout/suppression. Survit aux rotations d'écran ; l'UI ne fait qu'observer et déléguer.
 */
class AmbienceViewModel(app: Application) : AndroidViewModel(app) {

    private val repository = AmbienceTrackRepository(
        NenDatabase.getInstance(app).ambienceTrackDao()
    )

    val tracks: StateFlow<List<AmbienceTrack>> =
        repository.observeAll().stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList()
        )

    init {
        // Reprend le morceau choisi avant l'existence de la bibliothèque (AmbiencePrefs
        // gardait déjà une seule URI) pour qu'il apparaisse dans la liste au lieu de rester
        // invisible tout en continuant de jouer. add() ignore les doublons : sans danger de
        // le relancer à chaque création du ViewModel.
        val existing = AmbiencePrefs.trackUri(app)
        if (existing != null) {
            viewModelScope.launch { repository.add(existing.toString(), resolveDisplayName(existing)) }
        }
    }

    /** Ajoute un morceau nouvellement choisi via le sélecteur de fichiers. */
    fun add(uri: Uri) = viewModelScope.launch {
        repository.add(uri.toString(), resolveDisplayName(uri))
    }

    fun remove(track: AmbienceTrack) = viewModelScope.launch { repository.remove(track) }

    /** Nom du fichier tel qu'affiché par le fournisseur de contenu, sinon son dernier segment. */
    private fun resolveDisplayName(uri: Uri): String {
        val resolver = getApplication<Application>().contentResolver
        val queried = try {
            resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (index >= 0 && cursor.moveToFirst()) cursor.getString(index) else null
            }
        } catch (_: Exception) {
            null
        }
        return queried ?: uri.lastPathSegment ?: "Morceau"
    }
}
