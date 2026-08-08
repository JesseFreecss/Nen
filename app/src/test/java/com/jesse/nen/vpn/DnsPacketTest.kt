package com.jesse.nen.vpn

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DnsPacketTest {

    /** Construit une requête DNS minimale : en-tête + QNAME + QTYPE/QCLASS factices. */
    private fun buildQuery(domain: String, qdCount: Int = 1): ByteArray {
        val header = ByteArray(12)
        header[4] = ((qdCount ushr 8) and 0xFF).toByte()
        header[5] = (qdCount and 0xFF).toByte()

        val question = ByteArrayOutputStreamCompat()
        if (domain.isNotEmpty()) {
            for (label in domain.split('.')) {
                question.write(label.length)
                for (c in label) question.write(c.code)
            }
        }
        question.write(0) // fin du QNAME
        question.write(0); question.write(1) // QTYPE = A
        question.write(0); question.write(1) // QCLASS = IN

        return header + question.toByteArray()
    }

    @Test
    fun `extractDomain lit une requete valide`() {
        val query = buildQuery("www.example.com")
        assertEquals("www.example.com", DnsPacket.extractDomain(query))
    }

    @Test
    fun `extractDomain refuse un paquet trop court`() {
        assertNull(DnsPacket.extractDomain(ByteArray(5)))
    }

    @Test
    fun `extractDomain refuse un QDCOUNT nul`() {
        val query = buildQuery("example.com", qdCount = 0)
        assertNull(DnsPacket.extractDomain(query))
    }

    @Test
    fun `extractDomain refuse un nom tronque`() {
        // QNAME annonce un label de 10 octets mais le paquet s'arrête juste après.
        val header = ByteArray(12)
        header[5] = 1 // QDCOUNT = 1
        val truncated = header + byteArrayOf(10, 'e'.code.toByte(), 'x'.code.toByte())
        assertNull(DnsPacket.extractDomain(truncated))
    }

    @Test
    fun `extractDomain refuse un pointeur de compression`() {
        val header = ByteArray(12)
        header[5] = 1 // QDCOUNT = 1
        // 0xC0 = deux bits de poids fort à 1 : marqueur de pointeur de compression.
        val withPointer = header + byteArrayOf(0xC0.toByte(), 0x0C)
        assertNull(DnsPacket.extractDomain(withPointer))
    }

    @Test
    fun `buildNxDomainResponse pose QR et RCODE NXDOMAIN sans toucher a la question`() {
        val query = buildQuery("blocked.example")
        query[2] = 0x01 // simule RD=1 dans la requête d'origine
        query[6] = 0x00; query[7] = 0x05 // ANCOUNT=5 (doit être remis à 0 dans la réponse)

        val response = DnsPacket.buildNxDomainResponse(query)

        assertEquals(query.size, response.size)
        assertEquals(0x81, response[2].toInt() and 0xFF) // QR=1 conservé avec RD=1 d'origine
        assertEquals(0x83, response[3].toInt() and 0xFF) // RA=1, RCODE=3 (NXDOMAIN)
        assertEquals(0, response[6].toInt()); assertEquals(0, response[7].toInt()) // ANCOUNT=0
        assertEquals(0, response[8].toInt()); assertEquals(0, response[9].toInt()) // NSCOUNT=0
        assertEquals(0, response[10].toInt()); assertEquals(0, response[11].toInt()) // ARCOUNT=0
        // La section Question (à partir de l'octet 12) n'est pas modifiée.
        assertEquals("blocked.example", DnsPacket.extractDomain(response))
    }

    @Test
    fun `buildUdpPacket assemble des en-tetes coherents et une somme de controle IP valide`() {
        val srcIp = byteArrayOf(10, 0, 0, 1)
        val dstIp = byteArrayOf(10, 0, 0, 2)
        val payload = buildQuery("example.com")

        val packet = DnsPacket.buildUdpPacket(srcIp, dstIp, 12345, 53, payload)

        assertEquals(20 + 8 + payload.size, packet.size)
        assertEquals(0x45, packet[0].toInt() and 0xFF) // version 4, IHL 5

        // Ports UDP (big-endian) aux octets 20-23.
        val srcPort = ((packet[20].toInt() and 0xFF) shl 8) or (packet[21].toInt() and 0xFF)
        val dstPort = ((packet[22].toInt() and 0xFF) shl 8) or (packet[23].toInt() and 0xFF)
        assertEquals(12345, srcPort)
        assertEquals(53, dstPort)

        // La charge utile DNS est recopiée telle quelle à partir de l'octet 28.
        val embeddedPayload = packet.copyOfRange(28, packet.size)
        assertEquals(payload.toList(), embeddedPayload.toList())

        // Propriété de la somme de contrôle Internet : la somme (complément à 1) de tous les
        // mots de 16 bits de l'en-tête IPv4, checksum inclus, doit valoir 0xFFFF.
        assertEquals(0xFFFF, internetChecksumOver(packet, 0, 20))
    }

    /** Réimplémentation indépendante de la somme de contrôle, pour ne pas dépendre de la
     *  méthode privée de [DnsPacket] et vérifier son résultat depuis l'extérieur. */
    private fun internetChecksumOver(data: ByteArray, offset: Int, length: Int): Int {
        var sum = 0
        var i = offset
        val end = offset + length
        while (i + 1 < end) {
            sum += ((data[i].toInt() and 0xFF) shl 8) or (data[i + 1].toInt() and 0xFF)
            if (sum > 0xFFFF) sum = (sum and 0xFFFF) + 1
            i += 2
        }
        return sum and 0xFFFF
    }
}

/** Mini tampon d'octets pour ne dépendre d'aucune bibliothèque supplémentaire dans le test. */
private class ByteArrayOutputStreamCompat {
    private val bytes = mutableListOf<Byte>()
    fun write(value: Int) { bytes.add(value.toByte()) }
    fun toByteArray(): ByteArray = bytes.toByteArray()
}
