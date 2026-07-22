package com.jesse.gardefou.blocklist

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.jesse.gardefou.data.BlockedKeyword
import com.jesse.gardefou.data.GardeFouDatabase
import com.jesse.gardefou.data.KeywordRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * ViewModel de la liste de blocage : expose les mots-clés à l'UI et gère l'ajout/suppression.
 *
 * AndroidViewModel = ViewModel avec accès à l'Application (donc à la base Room).
 * Il survit aux rotations d'écran ; l'UI ne fait qu'observer et déléguer.
 */
class KeywordViewModel(app: Application) : AndroidViewModel(app) {

    private val repository = KeywordRepository(
        GardeFouDatabase.getInstance(app).blockedKeywordDao()
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
}
