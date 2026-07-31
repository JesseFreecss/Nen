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
 * version = 3 : ajout de la table `ambience_tracks`, la bibliothèque de morceaux
 * d'ambiance (voir MIGRATION_2_3).
 * exportSchema = false : on ne génère pas le fichier de schéma JSON (pas utile ici).
 */
@Database(
    entities = [BlockedKeyword::class, AmbienceTrack::class],
    version = 3,
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
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                    .build().also { INSTANCE = it }
            }
    }
}
