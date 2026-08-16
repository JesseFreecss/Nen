package com.jesse.nen.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Point d'entrée de la base Room. Liste les entités (tables) et expose les DAO.
 *
 * version = 4 : normalisation des mots-clés déjà en base (voir MIGRATION_3_4).
 * exportSchema = false : on ne génère pas le fichier de schéma JSON (pas utile ici).
 */
@Database(
    entities = [BlockedKeyword::class, AmbienceTrack::class],
    version = 4,
    exportSchema = false
)
abstract class NenDatabase : RoomDatabase() {

    abstract fun blockedKeywordDao(): BlockedKeywordDao
    abstract fun ambienceTrackDao(): AmbienceTrackDao

    companion object {
        // @Volatile : garantit que tous les threads voient la même instance à jour.
        @Volatile
        private var INSTANCE: NenDatabase? = null

        /**
         * Migration v1 -> v2 : crée l'index UNIQUE sur `keyword`.
         * On supprime d'abord les éventuels doublons (en gardant l'id le plus ancien),
         * sinon la création de l'index unique échouerait sur des données existantes.
         * Le nom d'index doit correspondre à celui généré par Room pour @Index(unique).
         */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "DELETE FROM blocked_keywords WHERE id NOT IN " +
                        "(SELECT MIN(id) FROM blocked_keywords GROUP BY keyword)"
                )
                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS " +
                        "index_blocked_keywords_keyword ON blocked_keywords(keyword)"
                )
            }
        }

        /** Migration v2 -> v3 : crée la table de bibliothèque de morceaux d'ambiance. */
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS ambience_tracks (" +
                        "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "uri TEXT NOT NULL, " +
                        "display_name TEXT NOT NULL, " +
                        "added_at INTEGER NOT NULL)"
                )
            }
        }

        /**
         * Migration v3 -> v4 : applique [DomainEntry.normalize] aux mots-clés déjà en base.
         *
         * KeywordRepository.add() normalise désormais les URLs collées ("https://fkbae.to"
         * -> "fkbae.to") avant insertion, mais les entrées ajoutées avant ce correctif sont
         * restées telles quelles en base — et donc jamais comparées avec succès aux noms
         * d'hôte bruts extraits des requêtes DNS par NenVpnService (blocage silencieusement
         * inopérant pour ces entrées-là).
         *
         * Traite les lignes par id croissant : si la forme normalisée d'une ligne a déjà été
         * vue (doublon révélé par la normalisation, ex. "https://fkbae.to" et "fkbae.to"
         * coexistants), la ligne la plus récente est supprimée plutôt que de violer l'index
         * UNIQUE sur `keyword`.
         */
        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                val seen = mutableSetOf<String>()
                val toDelete = mutableListOf<Long>()
                val toUpdate = mutableListOf<Pair<Long, String>>()
                db.query("SELECT id, keyword FROM blocked_keywords ORDER BY id").use { cursor ->
                    val idIdx = cursor.getColumnIndexOrThrow("id")
                    val keywordIdx = cursor.getColumnIndexOrThrow("keyword")
                    while (cursor.moveToNext()) {
                        val id = cursor.getLong(idIdx)
                        val keyword = cursor.getString(keywordIdx)
                        val normalized = DomainEntry.normalize(keyword)
                        if (normalized.isEmpty() || !seen.add(normalized)) {
                            toDelete.add(id)
                        } else if (normalized != keyword) {
                            toUpdate.add(id to normalized)
                        }
                    }
                }
                toDelete.forEach { id ->
                    db.execSQL("DELETE FROM blocked_keywords WHERE id = ?", arrayOf(id))
                }
                toUpdate.forEach { (id, normalized) ->
                    db.execSQL(
                        "UPDATE blocked_keywords SET keyword = ? WHERE id = ?",
                        arrayOf(normalized, id)
                    )
                }
            }
        }

        /**
         * Renvoie l'unique instance de la base (patron Singleton), en la créant au besoin.
         * Une seule connexion SQLite partagée par l'app (UI + VpnService).
         */
        fun getInstance(context: Context): NenDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    NenDatabase::class.java,
                    "nen.db"
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
                    .build().also { INSTANCE = it }
            }
    }
}
