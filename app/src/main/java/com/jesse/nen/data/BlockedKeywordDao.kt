package com.jesse.nen.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * DAO = Data Access Object : l'interface qui décrit les opérations sur la table.
 * Room génère automatiquement l'implémentation (le SQL réel) à la compilation.
 */
@Dao
interface BlockedKeywordDao {

    /**
     * Observe la liste complète. Retourne un Flow : chaque modification de la table
     * ré-émet automatiquement la nouvelle liste (idéal pour mettre l'UI à jour toute seule).
     */
    @Query("SELECT * FROM blocked_keywords ORDER BY created_at DESC")
    fun observeAll(): Flow<List<BlockedKeyword>>

    /**
     * Version "one-shot" (suspend) : lit juste les chaînes de mots-clés.
     * Utilisée par le VpnService pour charger la liste en mémoire.
     */
    @Query("SELECT keyword FROM blocked_keywords")
    suspend fun getAllKeywords(): List<String>

    // IGNORE : insérer un doublon (même mot-clé) ne provoque pas d'erreur, il est simplement ignoré.
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(keyword: BlockedKeyword)

    /**
     * Insertion en masse (import de liste). Room exécute automatiquement toutes les
     * insertions d'un même appel @Insert dans UNE SEULE transaction : c'est bien plus
     * rapide que des milliers d'insert() individuels, et atomique (tout ou rien).
     */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(keywords: List<BlockedKeyword>)

    @Delete
    suspend fun delete(keyword: BlockedKeyword)
}
