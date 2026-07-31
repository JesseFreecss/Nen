package com.jesse.nen.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Un morceau ajouté à la bibliothèque d'ambiance : son URI persistante (voir
 * `takePersistableUriPermission` dans MainActivity, sans quoi elle redeviendrait illisible
 * au redémarrage) et le nom affiché à l'utilisateur, lu une fois via ContentResolver.
 *
 * @Entity => Room crée une table SQLite "ambience_tracks" à partir de cette classe.
 */
@Entity(tableName = "ambience_tracks")
data class AmbienceTrack(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    @ColumnInfo(name = "uri")
    val uri: String,

    @ColumnInfo(name = "display_name")
    val displayName: String,

    @ColumnInfo(name = "added_at")
    val addedAt: Long = System.currentTimeMillis()
)
