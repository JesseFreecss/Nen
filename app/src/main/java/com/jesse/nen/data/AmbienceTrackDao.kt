package com.jesse.nen.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * DAO = Data Access Object : l'interface qui décrit les opérations sur la table.
 * Room génère automatiquement l'implémentation (le SQL réel) à la compilation.
 */
@Dao
interface AmbienceTrackDao {

    /** Ordre d'ajout : le premier morceau ajouté reste en tête de la bibliothèque. */
    @Query("SELECT * FROM ambience_tracks ORDER BY added_at ASC")
    fun observeAll(): Flow<List<AmbienceTrack>>

    @Query("SELECT EXISTS(SELECT 1 FROM ambience_tracks WHERE uri = :uri)")
    suspend fun exists(uri: String): Boolean

    @Insert
    suspend fun insert(track: AmbienceTrack)

    @Delete
    suspend fun delete(track: AmbienceTrack)
}
