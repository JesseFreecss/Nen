package com.jesse.nen.data

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

    /**
     * Ajoute un mot-clé (nettoyé + minuscules). Ignore les chaînes vides.
     *
     * Passe par [DomainEntry.normalize] pour ramener une URL collée (ex. copiée depuis un
     * navigateur, "https://fkbae.to/forum") à son domaine nu ("fkbae.to") : c'est ce que
     * [com.jesse.nen.vpn.NenVpnService] compare aux requêtes DNS.
     */
    suspend fun add(rawWord: String) {
        val word = DomainEntry.normalize(rawWord)
        if (word.isNotEmpty()) {
            dao.insert(BlockedKeyword(keyword = word))
        }
    }

    /** Supprime un mot-clé de la liste. */
    suspend fun remove(item: BlockedKeyword) = dao.delete(item)

    /**
     * Importe une liste de domaines au format "hosts file" (une entrée par ligne,
     * typiquement `0.0.0.0 domaine.com`). Parse les lignes, en extrait les domaines
     * valides, puis les insère en UNE SEULE transaction (insertAll). Les doublons
     * sont ignorés grâce à l'index unique + OnConflictStrategy.IGNORE.
     *
     * @param lines les lignes brutes du fichier.
     * @return le nombre de domaines valides envoyés à l'insertion.
     */
    suspend fun importFromList(lines: List<String>): Int {
        val domains = parseHostsLines(lines)
        if (domains.isEmpty()) return 0
        dao.insertAll(domains.map { BlockedKeyword(keyword = it) })
        return domains.size
    }

    /**
     * Extrait les noms de domaine d'une liste de lignes au format hosts.
     * - ignore les lignes vides et les commentaires (`#`, y compris en fin de ligne) ;
     * - accepte `0.0.0.0 domaine.com`, `127.0.0.1 domaine.com` ou juste `domaine.com` ;
     * - normalise en minuscules et déduplique (LinkedHashSet = ordre conservé).
     */
    private fun parseHostsLines(lines: List<String>): List<String> {
        val out = LinkedHashSet<String>()
        for (raw in lines) {
            val noComment = raw.substringBefore('#').trim()
            if (noComment.isEmpty()) continue
            val tokens = noComment.split(WHITESPACE)
            // Format hosts "IP domaine" -> le domaine est le 2e token ; sinon la ligne = domaine.
            val domain = (if (tokens.size >= 2) tokens[1] else tokens[0]).trim().lowercase()
            if (isValidDomain(domain)) out.add(domain)
        }
        return out.toList()
    }

    /** Filtre grossier : écarte les entrées locales et les chaînes qui ne sont pas des domaines. */
    private fun isValidDomain(d: String): Boolean {
        if (d.isEmpty() || d.length > 253) return false
        if (d in LOCAL_HOSTS) return false
        if (!d.contains('.')) return false
        return d.all { it.isLetterOrDigit() || it == '.' || it == '-' || it == '_' }
    }

    private companion object {
        val WHITESPACE = Regex("\\s+")
        val LOCAL_HOSTS = setOf("localhost", "localhost.localdomain", "local", "broadcasthost")
    }
}
