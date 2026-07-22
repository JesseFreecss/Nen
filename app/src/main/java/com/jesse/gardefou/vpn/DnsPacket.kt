package com.jesse.gardefou.vpn

/**
 * Utilitaires de bas niveau pour manipuler les paquets DNS et IPv4/UDP.
 *
 * Rappel du format d'un message DNS :
 *   [ en-tête 12 octets ][ section Question ][ ... réponses ]
 *   En-tête : ID(2) FLAGS(2) QDCOUNT(2) ANCOUNT(2) NSCOUNT(2) ARCOUNT(2)
 *   Question : QNAME (suite de labels longueur+octets, terminée par 0x00) QTYPE(2) QCLASS(2)
 *   QNAME "www.youtube.com" = [3]www[7]youtube[3]com[0]
 *
 * On ne traite ici que l'IPv4 + UDP (le transport classique du DNS sur le port 53).
 */
object DnsPacket {

    /**
     * Extrait le nom de domaine demandé dans une requête DNS.
     * Retourne null si le message est malformé ou vide.
     */
    fun extractDomain(dns: ByteArray): String? {
        if (dns.size < 12) return null
        val qdCount = readU16(dns, 4)
        if (qdCount < 1) return null

        val sb = StringBuilder()
        var pos = 12 // début de la section Question, juste après l'en-tête
        while (pos < dns.size) {
            val len = dns[pos].toInt() and 0xFF
            if (len == 0) break                 // 0x00 = fin du nom
            if (len and 0xC0 != 0) return null   // pointeur de compression : pas attendu dans une question
            pos++
            if (pos + len > dns.size) return null
            if (sb.isNotEmpty()) sb.append('.')
            sb.append(String(dns, pos, len, Charsets.US_ASCII))
            pos += len
        }
        return sb.toString().ifEmpty { null }
    }

    /**
     * Construit une réponse "domaine inexistant" (NXDOMAIN) à partir d'une requête.
     * L'appelant croit que le domaine n'existe pas -> connexion bloquée proprement.
     * On réutilise les octets de la requête (même ID, même question) et on change
     * seulement les drapeaux de l'en-tête.
     */
    fun buildNxDomainResponse(query: ByteArray): ByteArray {
        val response = query.copyOf()
        // Octet 2 : bit QR=1 (c'est une réponse) + on conserve Opcode et le bit RD.
        response[2] = (response[2].toInt() or 0x80).toByte()
        // Octet 3 : RA=1 (récursion dispo) + RCODE=3 (NXDOMAIN).
        response[3] = 0x83.toByte()
        // Aucune réponse fournie : ANCOUNT/NSCOUNT/ARCOUNT = 0.
        writeU16(response, 6, 0)  // ANCOUNT
        writeU16(response, 8, 0)  // NSCOUNT
        writeU16(response, 10, 0) // ARCOUNT
        return response
    }

    /**
     * Assemble un paquet IPv4 + UDP complet (en-têtes + charge utile DNS).
     * Utilisé pour renvoyer une réponse DNS dans l'interface TUN.
     * La somme de contrôle UDP est laissée à 0 (autorisé en IPv4), seule celle de
     * l'en-tête IPv4 est calculée (obligatoire).
     *
     * @param srcIp / dstIp : adresses IPv4 (4 octets chacune)
     * @param srcPort / dstPort : ports UDP
     * @param payload : message DNS
     */
    fun buildUdpPacket(
        srcIp: ByteArray,
        dstIp: ByteArray,
        srcPort: Int,
        dstPort: Int,
        payload: ByteArray
    ): ByteArray {
        val ipHeaderLen = 20
        val udpHeaderLen = 8
        val udpTotalLen = udpHeaderLen + payload.size
        val totalLen = ipHeaderLen + udpTotalLen
        val packet = ByteArray(totalLen)

        // --- En-tête IPv4 (20 octets) ---
        packet[0] = 0x45                       // Version 4, IHL 5 (=20 octets)
        packet[1] = 0                          // DSCP/ECN
        writeU16(packet, 2, totalLen)          // Longueur totale
        writeU16(packet, 4, 0)                 // Identification
        writeU16(packet, 6, 0x4000)            // Flags = Don't Fragment
        packet[8] = 64                         // TTL
        packet[9] = 17                         // Protocole = UDP
        // packet[10..11] = checksum, calculé plus bas
        System.arraycopy(srcIp, 0, packet, 12, 4)
        System.arraycopy(dstIp, 0, packet, 16, 4)
        writeU16(packet, 10, ipChecksum(packet, 0, ipHeaderLen))

        // --- En-tête UDP (8 octets) ---
        writeU16(packet, 20, srcPort)
        writeU16(packet, 22, dstPort)
        writeU16(packet, 24, udpTotalLen)      // Longueur UDP (en-tête + données)
        writeU16(packet, 26, 0)                // Checksum UDP = 0 (optionnel en IPv4)

        // --- Charge utile DNS ---
        System.arraycopy(payload, 0, packet, 28, payload.size)
        return packet
    }

    /** Somme de contrôle "internet" (complément à 1 sur 16 bits) d'une plage d'octets. */
    private fun ipChecksum(data: ByteArray, offset: Int, length: Int): Int {
        var sum = 0
        var i = offset
        val end = offset + length
        while (i + 1 < end) {
            sum += ((data[i].toInt() and 0xFF) shl 8) or (data[i + 1].toInt() and 0xFF)
            if (sum > 0xFFFF) sum = (sum and 0xFFFF) + 1
            i += 2
        }
        return sum.inv() and 0xFFFF
    }

    /** Lit un entier non signé 16 bits (big-endian) à la position donnée. */
    private fun readU16(data: ByteArray, pos: Int): Int =
        ((data[pos].toInt() and 0xFF) shl 8) or (data[pos + 1].toInt() and 0xFF)

    /** Écrit un entier 16 bits (big-endian) à la position donnée. */
    private fun writeU16(data: ByteArray, pos: Int, value: Int) {
        data[pos] = ((value ushr 8) and 0xFF).toByte()
        data[pos + 1] = (value and 0xFF).toByte()
    }
}
