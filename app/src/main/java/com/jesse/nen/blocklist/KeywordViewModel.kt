package com.jesse.nen.blocklist

import android.app.Application
import android.net.Uri
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.jesse.nen.data.BlockedKeyword
import com.jesse.nen.data.NenDatabase
import com.jesse.nen.data.KeywordRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * ViewModel de la liste de blocage : expose les mots-clés à l'UI et gère l'ajout/suppression.
 *
 * AndroidViewModel = ViewModel avec accès à l'Application (donc à la base Room).
 * Il survit aux rotations d'écran ; l'UI ne fait qu'observer et déléguer.
 */
class KeywordViewModel(app: Application) : AndroidViewModel(app) {

    private val repository = KeywordRepository(
        NenDatabase.getInstance(app).blockedKeywordDao()
    )

    /**
     * Liste observable des mots-clés, convertie depuis le Flow Room en StateFlow
     * (adapté à Compose). WhileSubscribed(5000) = arrête d'écouter la base 5 s après
     * que l'écran a disparu, pour économiser les ressources.
     */
    val keywords: StateFlow<List<BlockedKeyword>> =
        repository.observeAll().stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList()
        )

    fun add(word: String) = viewModelScope.launch { repository.add(word) }

    fun remove(item: BlockedKeyword) = viewModelScope.launch { repository.remove(item) }

    /** true pendant qu'un import de liste est en cours (pour afficher un indicateur). */
    private val _importing = MutableStateFlow(false)
    val importing: StateFlow<Boolean> = _importing.asStateFlow()

    /** Résultat du dernier import (nombre de domaines ajoutés), null si aucun / en cours. */
    private val _lastImportCount = MutableStateFlow<Int?>(null)
    val lastImportCount: StateFlow<Int?> = _lastImportCount.asStateFlow()

    /**
     * Importe une liste de domaines depuis un fichier choisi par l'utilisateur (format hosts).
     * Lit le fichier hors du thread principal puis délègue le parsing + l'insertion batch
     * au repository. Met à jour l'indicateur de progression pendant l'opération.
     */
    fun importFromUri(uri: Uri) = viewModelScope.launch {
        _importing.value = true
        _lastImportCount.value = null
        try {
            val lines = withContext(Dispatchers.IO) {
                getApplication<Application>().contentResolver
                    .openInputStream(uri)
                    ?.bufferedReader()
                    ?.use { it.readLines() }
                    ?: emptyList()
            }
            _lastImportCount.value = repository.importFromList(lines)
        } catch (e: Exception) {
            Log.w(TAG, "Import de liste échoué : ${e.message}")
            _lastImportCount.value = 0
        } finally {
            _importing.value = false
        }
    }

    private companion object {
        const val TAG = "KeywordViewModel"
    }
}
