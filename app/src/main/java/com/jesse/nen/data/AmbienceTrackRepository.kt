package com.jesse.nen.data

import kotlinx.coroutines.flow.Flow

/**
 * Repository de la bibliothèque de morceaux d'ambiance : couche fine entre le DAO et l'UI.
 */
class AmbienceTrackRepository(private val dao: AmbienceTrackDao) {

    /** Flux observable de la bibliothèque, dans l'ordre d'ajout (pour l'UI). */
    fun observeAll(): Flow<List<AmbienceTrack>> = dao.observeAll()

    /** Ignore l'ajout si l'URI est déjà dans la bibliothèque (re-sélection du même fichier). */
    suspend fun add(uri: String, displayName: String) {
        if (!dao.exists(uri)) {
            dao.insert(AmbienceTrack(uri = uri, displayName = displayName))
        }
    }

    suspend fun remove(track: AmbienceTrack) = dao.delete(track)
}
