package com.jesse.gardefou.data

import kotlinx.coroutines.flow.Flow

/**
 * Repository : couche fine entre le DAO et le reste de l'app (UI, VpnService).
 * Elle centralise les règles simples (ex. normaliser en minuscules) et évite que
 * l'UI dépende directement de Room. Bonne pratique d'architecture Android.
 */
class KeywordRepository(private val dao: BlockedKeywordDao) {

    /** Flux observable de tous les mots-clés (pour l'UI). */
    fun observeAll(): Flow<List<BlockedKeyword>> = dao.observeAll()

    /** Snapshot courant des mots-clés en minuscules (pour le VpnService). */
    suspend fun currentKeywords(): List<String> = dao.getAllKeywords()

    /** Ajoute un mot-clé (nettoyé + minuscules). Ignore les chaînes vides. */
    suspend fun add(rawWord: String) {
        val word = rawWord.trim().lowercase()
        if (word.isNotEmpty()) {
            dao.insert(BlockedKeyword(keyword = word))
        }
    }

    /** Supprime un mot-clé de la liste. */
    suspend fun remove(item: BlockedKeyword) = dao.delete(item)
}
