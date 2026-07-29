package com.jesse.nen.vpn

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.net.IpPrefix
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import android.util.Log
import com.jesse.nen.MainActivity
import com.jesse.nen.data.NenDatabase
import com.jesse.nen.data.KeywordRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress

/**
 * Service VPN local de Nen.
 *
 * Principe (design "DNS sinkhole", comme DNS66/NetGuard) :
 *  - On monte une interface VPN (TUN) qui déclare un serveur DNS fictif (10.111.0.1)
 *    et ne route QUE le trafic vers cette adresse. Résultat : seules les requêtes DNS
 *    du système passent par nous, tout le reste du trafic est inchangé.
 *  - Pour chaque requête DNS lue, on extrait le domaine et on le compare aux mots-clés.
 *      • correspondance -> on renvoie NXDOMAIN (domaine "inexistant") = blocage.
 *      • sinon          -> on relaie la requête à un vrai résolveur (8.8.8.8) via un
 *                          socket "protégé" (hors VPN) et on réinjecte la réponse.
 *
 * Limite connue : le DNS chiffré (DNS-over-HTTPS/TLS, "DNS privé") contourne ce filtre.
 */
class NenVpnService : VpnService() {

    // Portée coroutine du service : annulée à l'arrêt (SupervisorJob = un échec n'arrête pas les autres).
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private var vpnInterface: ParcelFileDescriptor? = null
    @Volatile private var running = false

    // Démarrage en cours mais tunnel pas encore monté (cas du démarrage différé au boot) :
    // évite qu'un second onStartCommand ne relance tout en parallèle.
    @Volatile private var starting = false

    // Règles de blocage gardées EN MÉMOIRE (rechargées automatiquement depuis la base),
    // pour ne jamais interroger Room à chaque paquet DNS (chemin critique en performance).
    //
    // Deux structures selon la nature de l'entrée :
    //  - blockedDomains : domaines exacts (ex. issus d'un import "hosts") -> Set pour un
    //    test O(1) par suffixe (www.youtube.com, youtube.com, com...). Idéal pour de très
    //    grandes listes (plusieurs milliers de domaines).
    //  - blockedKeywords : mots-clés courts saisis par l'utilisateur -> test par sous-chaîne
    //    (liste courte, le coût linéaire reste négligeable).
    @Volatile private var blockedDomains: Set<String> = emptySet()
    @Volatile private var blockedKeywords: List<String> = emptyList()

    // Verrou pour sérialiser les écritures vers l'interface TUN (plusieurs coroutines écrivent).
    private val writeLock = Any()
    private var outStream: FileOutputStream? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                // Arrêt DEMANDÉ par l'utilisateur : on oublie l'intention, pour ne pas
                // relancer la protection au prochain démarrage du téléphone.
                ProtectionPrefs.setEnabled(this, false)
                stopVpn()
                return START_NOT_STICKY
            }
            // intent est null quand le système redémarre le service après l'avoir tué
            // (conséquence de START_STICKY) : on retombe ici et le VPN se remonte seul.
            else -> startVpn(intent?.getLongExtra(EXTRA_START_DELAY_MS, 0L) ?: 0L)
        }
        return START_STICKY
    }

    /**
     * @param delayMs délai avant d'établir le tunnel. Utilisé au démarrage du téléphone, où
     *   monter le tunnel trop tôt le fait révoquer par le système (voir [BootReceiver]).
     */
    private fun startVpn(delayMs: Long) {
        if (running || starting) return
        starting = true

        // 1) Foreground obligatoire : notification persistante + démarrage prioritaire.
        //    À faire immédiatement, même si le tunnel est différé : le système exige
        //    startForeground() dans les secondes qui suivent startForegroundService().
        startAsForeground()

        // 2) Observe la base : la liste de mots-clés se met à jour toute seule.
        val repo = KeywordRepository(NenDatabase.getInstance(this).blockedKeywordDao())
        scope.launch {
            repo.observeAll().collect { list ->
                val domains = HashSet<String>()
                val keywords = ArrayList<String>()
                for (item in list) {
                    val k = item.keyword
                    if (k.isEmpty()) continue
                    // Un domaine complet (contient un point, pas d'espace) -> Set exact ;
                    // sinon c'est un mot-clé -> correspondance par sous-chaîne.
                    if (k.contains('.') && !k.contains(' ')) domains.add(k) else keywords.add(k)
                }
                blockedDomains = domains
                blockedKeywords = keywords
            }
        }

        // 3) Monte l'interface VPN, éventuellement après un délai.
        scope.launch {
            if (delayMs > 0) {
                Log.i(TAG, "Établissement du tunnel différé de ${delayMs / 1000} s")
                delay(delayMs)
            }
            establishTunnel()
        }
    }

    /** Construit et active l'interface TUN, puis démarre la boucle de lecture. */
    private fun establishTunnel() {
        val builder = Builder()
            .setSession("Nen")
            .addAddress(VPN_ADDRESS, 32)        // adresse locale de l'interface
            .addDnsServer(VPN_DNS)              // DNS système redirigé vers notre adresse fictive
            .addRoute(VPN_DNS, 32)             // on ne route QUE le trafic DNS vers nous
            .setBlocking(true)

        // Android Auto sans fil : la connexion passe par un lien Wi-Fi Direct (plage
        // 192.168.49.0/24) piloté par l'app "gearhead". On l'exclut du VPN pour ne pas
        // gêner l'appairage. excludeRoute() n'existe qu'à partir d'Android 13 (API 33) ;
        // sur les versions antérieures ce n'est pas nécessaire car on ne route de toute
        // façon que le trafic DNS (VPN_DNS/32), donc la plage Wi-Fi Direct n'est pas captée.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            builder.excludeRoute(IpPrefix(InetAddress.getByName(WIFI_DIRECT_SUBNET), 24))
        }

        // Exclut aussi l'app Android Auto elle-même du tunnel (défense en profondeur).
        // addDisallowedApplication lève NameNotFoundException si l'app n'est pas installée.
        try {
            builder.addDisallowedApplication(ANDROID_AUTO_PACKAGE)
        } catch (e: PackageManager.NameNotFoundException) {
            Log.d(TAG, "Android Auto (gearhead) non installé, exclusion ignorée")
        }

        val tun = builder.establish()

        if (tun == null) {
            Log.e(TAG, "establish() a renvoyé null (permission VPN absente ?)")
            stopVpn()
            return
        }

        vpnInterface = tun
        outStream = FileOutputStream(tun.fileDescriptor)
        running = true
        starting = false
        VpnStateHolder.setRunning(true)
        // Le VPN tourne : on note l'intention pour pouvoir le relancer après un redémarrage.
        ProtectionPrefs.setEnabled(this, true)

        // 4) Lance la boucle de lecture des paquets.
        scope.launch { runPacketLoop(tun) }
        Log.i(TAG, "VPN démarré")
    }

    /** Boucle principale : lit un paquet IP, le traite s'il s'agit d'une requête DNS. */
    private fun runPacketLoop(tun: ParcelFileDescriptor) {
        val input = FileInputStream(tun.fileDescriptor)
        val buffer = ByteArray(32_767)
        try {
            while (running) {
                val length = input.read(buffer)
                if (length <= 0) continue
                handlePacket(buffer.copyOf(length))
            }
        } catch (e: Exception) {
            if (running) Log.w(TAG, "Boucle de paquets interrompue : ${e.message}")
        }
        // Sortie de boucle alors qu'on se croit actif (interface fermée par le système,
        // changement de réseau...) : plus aucun paquet n'est filtré. On arrête pour de bon
        // plutôt que d'afficher une protection active qui ne protège plus rien.
        if (running) {
            Log.w(TAG, "Boucle de paquets terminée de façon inattendue, arrêt du VPN")
            stopVpn()
        }
    }

    /** Analyse un paquet IPv4/UDP ; ne garde que les requêtes DNS (port 53). */
    private fun handlePacket(packet: ByteArray) {
        if (packet.size < 28) return
        val version = (packet[0].toInt() ushr 4) and 0xF
        if (version != 4) return                       // on ne gère que l'IPv4
        val ihl = (packet[0].toInt() and 0xF) * 4       // longueur de l'en-tête IP
        val protocol = packet[9].toInt() and 0xFF
        if (protocol != 17) return                      // 17 = UDP

        val srcIp = packet.copyOfRange(12, 16)
        val dstIp = packet.copyOfRange(16, 20)
        val srcPort = readU16(packet, ihl)
        val dstPort = readU16(packet, ihl + 2)
        if (dstPort != 53) return                       // pas du DNS

        val dnsStart = ihl + 8                           // après en-têtes IP + UDP
        if (dnsStart >= packet.size) return
        val dnsQuery = packet.copyOfRange(dnsStart, packet.size)
        val domain = DnsPacket.extractDomain(dnsQuery)

        if (domain != null && isBlocked(domain)) {
            // Réponse NXDOMAIN : on inverse source/destination.
            val response = DnsPacket.buildNxDomainResponse(dnsQuery)
            val reply = DnsPacket.buildUdpPacket(dstIp, srcIp, dstPort, srcPort, response)
            writePacket(reply)
            Log.i(TAG, "BLOQUÉ : $domain")
        } else {
            // Relais vers le vrai résolveur, puis réinjection de la réponse.
            scope.launch {
                val response = forwardToUpstream(dnsQuery) ?: return@launch
                val reply = DnsPacket.buildUdpPacket(dstIp, srcIp, dstPort, srcPort, response)
                writePacket(reply)
            }
        }
    }

    /** true si le domaine est bloqué (insensible à la casse). */
    private fun isBlocked(domain: String): Boolean {
        val lower = domain.lowercase()

        // 1) Correspondance exacte de domaine ou d'un domaine parent (rapide, O(nb de labels)).
        //    Ex. pour "www.youtube.com" on teste "www.youtube.com", "youtube.com", "com".
        if (blockedDomains.isNotEmpty()) {
            var candidate = lower
            while (true) {
                if (blockedDomains.contains(candidate)) return true
                val dot = candidate.indexOf('.')
                if (dot < 0) break
                candidate = candidate.substring(dot + 1)
            }
        }

        // 2) Mots-clés utilisateur : correspondance par sous-chaîne.
        return blockedKeywords.any { it.isNotEmpty() && lower.contains(it) }
    }

    /** Envoie la requête DNS à un résolveur externe via un socket protégé (hors VPN). */
    private fun forwardToUpstream(query: ByteArray): ByteArray? {
        return try {
            DatagramSocket().use { socket ->
                protect(socket)                 // CRUCIAL : ce socket ne repasse pas par le VPN
                socket.soTimeout = 5_000
                val server = InetAddress.getByName(UPSTREAM_DNS)
                socket.send(DatagramPacket(query, query.size, server, 53))
                val buf = ByteArray(4_096)
                val resp = DatagramPacket(buf, buf.size)
                socket.receive(resp)
                buf.copyOf(resp.length)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Relais DNS échoué : ${e.message}")
            null
        }
    }

    /** Écrit un paquet dans l'interface TUN (synchronisé entre coroutines). */
    private fun writePacket(packet: ByteArray) {
        synchronized(writeLock) {
            try {
                outStream?.write(packet)
            } catch (e: Exception) {
                Log.w(TAG, "Écriture TUN échouée : ${e.message}")
            }
        }
    }

    private fun stopVpn() {
        running = false
        starting = false
        VpnStateHolder.setRunning(false)
        try {
            vpnInterface?.close()
        } catch (_: Exception) {
        }
        vpnInterface = null
        outStream = null
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
        Log.i(TAG, "VPN arrêté")
        // En dernier : stopVpn() peut être appelé DEPUIS une coroutine du scope (boucle de
        // paquets), et scope.cancel() interromprait alors le reste de cette méthode.
        scope.cancel()
    }

    /**
     * Appelé par le système quand l'autorisation VPN nous est retirée : l'utilisateur l'a
     * révoquée, ou une autre app a pris la main (Android n'autorise qu'un seul VPN actif).
     *
     * L'implémentation par défaut se contente d'un stopSelf() brutal, qui laisserait l'app
     * afficher « protection activée » alors que le tunnel est mort. On passe donc par
     * stopVpn() pour remettre l'état à plat.
     *
     * On ne touche pas à ProtectionPrefs : l'utilisateur n'a pas renoncé à la protection,
     * elle sera donc retentée au prochain redémarrage.
     */
    override fun onRevoke() {
        Log.w(TAG, "Autorisation VPN révoquée (autre app VPN ?), arrêt")
        stopVpn()
        super.onRevoke()
    }

    override fun onDestroy() {
        super.onDestroy()
        if (running) stopVpn()
    }

    // --- Foreground / notification ---

    private fun startAsForeground() {
        val manager = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Protection Nen",
                NotificationManager.IMPORTANCE_LOW
            ).apply { description = "Indique que la protection est active" }
            manager.createNotificationChannel(channel)
        }

        val openApp = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )

        val notification: Notification = Notification.Builder(this, CHANNEL_ID)
            .setContentTitle("Nen")
            .setContentText("Ten actif — filtrage DNS")
            .setSmallIcon(com.jesse.nen.R.mipmap.ic_launcher)
            .setContentIntent(openApp)
            .setOngoing(true)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun readU16(data: ByteArray, pos: Int): Int =
        ((data[pos].toInt() and 0xFF) shl 8) or (data[pos + 1].toInt() and 0xFF)

    companion object {
        private const val TAG = "NenVpn"

        private const val VPN_ADDRESS = "10.111.0.2"   // adresse de l'interface locale
        private const val VPN_DNS = "10.111.0.1"       // DNS fictif intercepté par nous
        private const val UPSTREAM_DNS = "8.8.8.8"      // résolveur réel pour les domaines autorisés

        // Android Auto sans fil : plage Wi-Fi Direct et paquet de l'app à exclure du VPN.
        private const val WIFI_DIRECT_SUBNET = "192.168.49.0"
        private const val ANDROID_AUTO_PACKAGE = "com.google.android.projection.gearhead"

        private const val CHANNEL_ID = "nen_protection"
        private const val NOTIFICATION_ID = 1

        const val ACTION_START = "com.jesse.nen.vpn.START"
        const val ACTION_STOP = "com.jesse.nen.vpn.STOP"

        private const val EXTRA_START_DELAY_MS = "start_delay_ms"

        /**
         * Délai avant de monter le tunnel lors d'un démarrage du téléphone.
         *
         * Fenêtre de tir étroite, mesurée sur l'appareil :
         *  - avant ~3,5 s : un composant privilégié du constructeur appelle `prepareVpn()`
         *    pendant l'initialisation du système, ce qui révoque le tunnel qu'on vient de
         *    monter ;
         *  - après ~10 s : l'exemption temporaire accordée à l'app pour traiter
         *    BOOT_COMPLETED expire (`temporaryAppAllowlistDuration=10000`), et `establish()`
         *    est alors refusé car l'app est en arrière-plan.
         *
         * Le « VPN permanent » d'Android (always-on) est la solution robuste à ce problème :
         * le système démarre alors le service lui-même, sans dépendre de cette fenêtre.
         */
        const val BOOT_START_DELAY_MS = 8_000L

        /**
         * Démarre le service (à appeler APRÈS avoir obtenu la permission VPN).
         *
         * @param delayMs délai avant d'établir le tunnel ; 0 pour un démarrage immédiat.
         */
        fun start(context: Context, delayMs: Long = 0L) {
            val intent = Intent(context, NenVpnService::class.java)
                .setAction(ACTION_START)
                .putExtra(EXTRA_START_DELAY_MS, delayMs)
            context.startForegroundService(intent)
        }

        /** Demande l'arrêt du service. */
        fun stop(context: Context) {
            val intent = Intent(context, NenVpnService::class.java).setAction(ACTION_STOP)
            context.startService(intent)
        }
    }
}
