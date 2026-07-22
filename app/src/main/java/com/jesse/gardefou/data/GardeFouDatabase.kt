package com.jesse.gardefou.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

/**
 * Point d'entrée de la base Room. Liste les entités (tables) et expose les DAO.
 *
 * version = 1 : à incrémenter quand on modifiera le schéma (avec une migration).
 * exportSchema = false : on ne génère pas le fichier de schéma JSON (pas utile ici).
 */
@Database(
    entities = [BlockedKeyword::class],
    version = 1,
    exportSchema = false
)
abstract class GardeFouDatabase : RoomDatabase() {

    abstract fun blockedKeywordDao(): BlockedKeywordDao

    companion object {
        // @Volatile : garantit que tous les threads voient la même instance à jour.
        @Volatile
        private var INSTANCE: GardeFouDatabase? = null

        /**
         * Renvoie l'unique instance de la base (patron Singleton), en la créant au besoin.
         * Une seule connexion SQLite partagée par l'app (UI + VpnService).
         */
        fun getInstance(context: Context): GardeFouDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    GardeFouDatabase::class.java,
                    "gardefou.db"
                ).build().also { INSTANCE = it }
            }
    }
}
