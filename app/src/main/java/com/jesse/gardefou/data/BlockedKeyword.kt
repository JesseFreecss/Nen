package com.jesse.gardefou.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Une entrée de la liste de blocage : un mot-clé qui, s'il apparaît dans un nom
 * de domaine demandé (ex. "youtube" dans "www.youtube.com"), fera bloquer la requête.
 *
 * @Entity => Room crée une table SQLite "blocked_keywords" à partir de cette classe.
 * Chaque propriété devient une colonne.
 *
 * Index UNIQUE sur `keyword` : accélère les recherches et rend l'import en masse
 * idempotent (les doublons sont ignorés grâce à OnConflictStrategy.IGNORE).
 */
@Entity(
    tableName = "blocked_keywords",
    indices = [Index(value = ["keyword"], unique = true)]
)
data class BlockedKeyword(
    // Clé primaire auto-incrémentée (0 = "laisse Room choisir l'id à l'insertion").
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    // Le mot-clé, stocké en minuscules (comparaison insensible à la casse).
    @ColumnInfo(name = "keyword")
    val keyword: String,

    // Horodatage de création, pour trier la liste (plus récent d'abord).
    @ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis()
)
