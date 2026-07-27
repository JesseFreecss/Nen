package com.jesse.gardefou

import android.Manifest
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.net.VpnService
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.jesse.gardefou.accessibility.A11yHeartbeat
import com.jesse.gardefou.accessibility.NenAccessibilityService
import com.jesse.gardefou.blocklist.EmptyVows
import com.jesse.gardefou.blocklist.KeywordViewModel
import com.jesse.gardefou.blocklist.SealVowDialog
import com.jesse.gardefou.blocklist.SealedVows
import com.jesse.gardefou.blocklist.VowUnlock
import com.jesse.gardefou.blocklist.VowsFooter
import com.jesse.gardefou.blocklist.VowsHeader
import com.jesse.gardefou.ui.NeteroGate
import com.jesse.gardefou.ui.theme.NenTheme
import com.jesse.gardefou.vpn.NenVpnService
import com.jesse.gardefou.vpn.ProtectionPrefs
import com.jesse.gardefou.vpn.VpnStateHolder
import kotlinx.coroutines.delay

/**
 * Point d'entrée de l'application (activité de lancement).
 */
/**
 * FragmentActivity et non ComponentActivity : BiometricPrompt l'exige pour rattacher son
 * invite système au cycle de vie de l'écran.
 */
class MainActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            NenTheme {
                // `remember` et non `rememberSaveable` : l'état sauvegardé survit à la mort
                // du processus, et la porte se trouvait alors sautée au retour. Elle doit
                // s'ouvrir à chaque entrée dans l'app. Contrepartie assumée : une rotation
                // de l'écran la fait réapparaître.
                var entered by remember { mutableStateOf(false) }

                if (!entered) {
                    NeteroGate(onEnter = { entered = true })
                } else {
                    Scaffold(
                        modifier = Modifier.fillMaxSize(),
                        containerColor = MaterialTheme.colorScheme.background
                    ) { innerPadding ->
                        ProtectionScreen(modifier = Modifier.padding(innerPadding))
                    }
                }
            }
        }
    }
}

/**
 * Écran principal (« Grimoire ») : état de la protection avec sa durée de série, failles à
 * corriger, et liste des vœux scellés (mots-clés bloqués).
 *
 * Le vocabulaire suit la maquette docs/design/maquette-grimoire.png : la protection est le
 * « Ten », une règle de blocage un « vœu scellé ».
 */
@Composable
fun ProtectionScreen(
    modifier: Modifier = Modifier,
    keywordViewModel: KeywordViewModel = viewModel()
) {
    val context = LocalContext.current

    // État marche/arrêt du VPN, observé depuis le service via VpnStateHolder.
    val isProtected by VpnStateHolder.running.collectAsStateWithLifecycle()

    // Vœux scellés + état de l'import de liste, observés depuis le ViewModel.
    val keywords by keywordViewModel.keywords.collectAsStateWithLifecycle()
    val importing by keywordViewModel.importing.collectAsStateWithLifecycle()
    val lastImportCount by keywordViewModel.lastImportCount.collectAsStateWithLifecycle()

    var showSealDialog by remember { mutableStateOf(false) }

    // Vœu actuellement révélé. Il se rescelle tout seul : la révélation est une exception,
    // pas un état dans lequel on s'installe.
    var revealedVowId by remember { mutableStateOf<Long?>(null) }
    LaunchedEffect(revealedVowId) {
        if (revealedVowId != null) {
            delay(REVEAL_DURATION_MS)
            revealedVowId = null
        }
    }

    // BiometricPrompt s'accroche à l'activité hôte, pas au contexte Compose.
    val hostActivity = context as? FragmentActivity

    fun requestReveal(vowId: Long) {
        val activity = hostActivity ?: return
        VowUnlock.prompt(
            activity = activity,
            onSuccess = { revealedVowId = vowId },
            onUnavailable = { message ->
                Toast.makeText(
                    context,
                    "Déverrouillage impossible : $message",
                    Toast.LENGTH_LONG
                ).show()
            }
        )
    }

    // Sélecteur de fichier système : renvoie l'Uri du fichier hosts choisi (ou null si annulé).
    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) keywordViewModel.importFromUri(uri)
    }

    // État "service d'accessibilité activé ?". Rafraîchi à chaque retour au premier plan
    // (ON_RESUME), notamment quand l'utilisateur revient des réglages système.
    var a11yEnabled by remember { mutableStateOf(isAccessibilityServiceEnabled(context)) }

    // Le service tourne-t-il RÉELLEMENT ? Le réglage système ci-dessus reste sur « activé »
    // même quand HyperOS a tué le processus ; seul le battement de cœur le dit.
    var a11yAlive by remember { mutableStateOf(A11yHeartbeat.isAlive(context)) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(30_000)
            a11yAlive = A11yHeartbeat.isAlive(context)
        }
    }

    // État « app exclue des optimisations de batterie ? ». Sans cette exclusion, le système
    // finit par tuer le service VPN en arrière-plan et la protection se coupe toute seule.
    var batteryExempt by remember { mutableStateOf(isIgnoringBatteryOptimizations(context)) }

    // Début de la série en cours. Relu à chaque bascule : c'est le service qui l'écrit.
    var enabledSince by remember { mutableLongStateOf(ProtectionPrefs.enabledSince(context)) }
    LaunchedEffect(isProtected) { enabledSince = ProtectionPrefs.enabledSince(context) }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                a11yEnabled = isAccessibilityServiceEnabled(context)
                a11yAlive = A11yHeartbeat.isAlive(context)
                batteryExempt = isIgnoringBatteryOptimizations(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // Lanceur pour la boîte de dialogue système "Autoriser Nen à configurer un VPN ?".
    // Si l'utilisateur accepte (RESULT_OK), on démarre réellement le service.
    val vpnPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            NenVpnService.start(context)
        }
    }

    // Demande la permission de notification (Android 13+) au premier affichage,
    // pour que la notification de protection soit visible.
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* accordée ou non : le service fonctionne dans les deux cas */ }

    // Reprise après un arrêt subi : si l'utilisateur avait laissé la protection active mais
    // que le VPN ne tourne pas (service tué par le système, et pas toujours relancé par
    // Android), on le remonte dès l'ouverture de l'app. Sans autorisation VPN encore valide
    // on ne fait rien : ce serait ouvrir une boîte de dialogue sans que l'utilisateur ait
    // rien demandé.
    LaunchedEffect(isProtected) {
        if (!isProtected && ProtectionPrefs.isEnabled(context) &&
            VpnService.prepare(context) == null
        ) {
            NenVpnService.start(context)
        }
    }

    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                context, Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    // Action de la zone d'état : bascule le VPN.
    fun onToggleProtection() {
        if (isProtected) {
            NenVpnService.stop(context)
        } else {
            // prepare() renvoie un Intent si l'autorisation VPN n'a pas encore été donnée.
            val prepareIntent: Intent? = VpnService.prepare(context)
            if (prepareIntent != null) {
                vpnPermissionLauncher.launch(prepareIntent)
            } else {
                NenVpnService.start(context)
            }
        }
    }

    // Toute la page défile d'un seul bloc, comme la maquette. Une liste à défilement interne
    // se retrouverait écrasée à quelques dizaines de dp dès qu'une carte « Faille » s'affiche.
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp)
    ) {
        item { GrimoireHeader(modifier = Modifier.padding(top = 32.dp)) }

        item {
            TenStatus(
                isProtected = isProtected,
                enabledSince = enabledSince,
                onToggle = { onToggleProtection() },
                modifier = Modifier.padding(top = 28.dp)
            )
        }

        // Failles : on n'affiche un bloc que si le réglage manque, pour ne pas encombrer.
        // Android ne permet pas d'activer le service d'accessibilité par programme : on se
        // contente de guider l'utilisateur vers la bonne page des réglages.
        if (!a11yEnabled) {
            item {
                FailleCard(
                    title = "Faille : surveillance in-app",
                    message = "Pour filtrer les URL dans les navigateurs et détecter les "
                        + "YouTube Shorts, activez « Protection Nen » dans les réglages "
                        + "d'accessibilité.",
                    actionLabel = "Ouvrir les réglages d'accessibilité",
                    onAction = {
                        context.startActivity(
                            Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        )
                    },
                    modifier = Modifier.padding(top = 24.dp)
                )
            }
        }

        // Service activé au niveau système, mais qui ne bat plus : le processus a été tué.
        // Rouvrir les réglages et basculer l'interrupteur le relance.
        if (a11yEnabled && !a11yAlive) {
            item {
                FailleCard(
                    title = "Faille : surveillance interrompue",
                    message = "« Protection Nen » est activée dans les réglages, mais le "
                        + "service ne répond plus : le système l'a arrêté. Rien n'est "
                        + "surveillé pour le moment. Désactivez puis réactivez-la dans les "
                        + "réglages d'accessibilité pour la relancer.",
                    actionLabel = "Ouvrir les réglages d'accessibilité",
                    onAction = {
                        context.startActivity(
                            Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        )
                    },
                    modifier = Modifier.padding(top = 24.dp)
                )
            }
        }

        // Optimisations de batterie : cause n°1 des coupures spontanées de la protection.
        if (!batteryExempt) {
            item {
                FailleCard(
                    title = "Faille : batterie",
                    message = "Sans exclusion des optimisations de batterie, le système peut "
                        + "arrêter Nen en arrière-plan et la protection se coupe toute "
                        + "seule. Sur Xiaomi, pensez aussi à autoriser le « démarrage "
                        + "automatique » et à mettre l'économiseur de batterie sur « Aucune "
                        + "restriction » pour cette app.",
                    actionLabel = "Exclure des optimisations de batterie",
                    onAction = { requestIgnoreBatteryOptimizations(context) },
                    modifier = Modifier.padding(top = 12.dp)
                )
            }
        }

        item { VowsHeader(count = keywords.size, modifier = Modifier.padding(top = 32.dp)) }

        if (keywords.isEmpty()) {
            item { EmptyVows(modifier = Modifier.padding(top = 20.dp)) }
        } else {
            item {
                SealedVows(
                    keywords = keywords,
                    revealedId = revealedVowId,
                    onOrbClick = { vow -> requestReveal(vow.id) },
                    onUnseal = { vow -> keywordViewModel.remove(vow) },
                    modifier = Modifier.padding(top = 20.dp)
                )
            }
        }

        item {
            VowsFooter(
                importing = importing,
                lastImportCount = lastImportCount,
                onSeal = { showSealDialog = true },
                onImport = { importLauncher.launch("text/*") },
                modifier = Modifier.padding(top = 24.dp, bottom = 16.dp)
            )
        }
    }

    if (showSealDialog) {
        SealVowDialog(
            onDismiss = { showSealDialog = false },
            onSeal = { keyword ->
                keywordViewModel.add(keyword)
                showSealDialog = false
            }
        )
    }
}

/** Sur-titre « GRIMOIRE » et titre de l'app, en haut de l'écran. */
@Composable
private fun GrimoireHeader(modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        Text(
            text = "GRIMOIRE",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = "Nen",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(top = 6.dp)
        )
    }
}

/**
 * Durée de la série en cours et état du Ten. Toute la zone est cliquable : c'est le seul
 * moyen d'activer ou de couper la protection (la maquette n'a pas de bouton dédié).
 */
@Composable
private fun TenStatus(
    isProtected: Boolean,
    enabledSince: Long,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier
) {
    // Ré-horloge toutes les minutes pour faire avancer le compteur sans quitter l'écran.
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(isProtected, enabledSince) {
        while (isProtected && enabledSince > 0L) {
            now = System.currentTimeMillis()
            delay(60_000)
        }
    }

    val segments = if (isProtected && enabledSince > 0L) {
        streakSegments(now - enabledSince)
    } else {
        emptyList()
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(
                onClickLabel = if (isProtected) "Rompre le Ten" else "Tisser le Ten",
                onClick = onToggle
            )
            .padding(vertical = 8.dp)
    ) {
        if (segments.isEmpty()) {
            Text(
                text = "—",
                style = MaterialTheme.typography.displayMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            Row(verticalAlignment = Alignment.Bottom) {
                segments.forEach { (value, unit) ->
                    Text(
                        text = value,
                        style = MaterialTheme.typography.displayMedium,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Text(
                        text = unit,
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 3.dp, end = 12.dp, bottom = 6.dp)
                    )
                }
            }
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.padding(top = 10.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(
                        if (isProtected) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant
                    )
            )
            Text(
                text = if (isProtected) "Ten actif" else "Ten dormant",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onBackground
            )
        }
    }
}

/**
 * Découpe une durée en au plus deux segments (valeur, unité), du plus significatif au moins :
 * « 3 j 14 h », « 14 h 22 m », puis « 22 m » sous l'heure.
 */
private fun streakSegments(elapsedMs: Long): List<Pair<String, String>> {
    val totalMinutes = (elapsedMs.coerceAtLeast(0L)) / 60_000
    val days = totalMinutes / 1440
    val hours = (totalMinutes % 1440) / 60
    val minutes = totalMinutes % 60
    return when {
        days > 0 -> listOf(days.toString() to "j", hours.toString() to "h")
        hours > 0 -> listOf(hours.toString() to "h", minutes.toString() to "m")
        else -> listOf(minutes.toString() to "m")
    }
}

/** Carte d'avertissement pour un réglage manquant qui fragilise la protection. */
@Composable
private fun FailleCard(
    title: String,
    message: String,
    actionLabel: String,
    onAction: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(start = 16.dp, top = 14.dp, end = 16.dp, bottom = 6.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.error
            )
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 6.dp)
            )
            TextButton(
                onClick = onAction,
                modifier = Modifier.padding(top = 4.dp)
            ) {
                Text(
                    text = actionLabel,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

/**
 * Indique si le service d'accessibilité de Nen est actuellement activé par l'utilisateur.
 * On lit la liste système des services activés (Settings.Secure) et on y cherche notre composant.
 */
private fun isAccessibilityServiceEnabled(context: Context): Boolean {
    val expected = ComponentName(context, NenAccessibilityService::class.java)
    val enabledServices = Settings.Secure.getString(
        context.contentResolver,
        Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
    ) ?: return false
    return enabledServices.split(':').any { entry ->
        ComponentName.unflattenFromString(entry) == expected
    }
}

/** Indique si l'app est déjà exclue des optimisations de batterie. */
private fun isIgnoringBatteryOptimizations(context: Context): Boolean {
    val pm = context.getSystemService(PowerManager::class.java) ?: return false
    return pm.isIgnoringBatteryOptimizations(context.packageName)
}

/**
 * Ouvre la demande système d'exclusion des optimisations de batterie.
 *
 * ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS affiche directement la boîte de dialogue
 * « Autoriser ? », mais certaines surcouches ne l'implémentent pas : on se replie alors sur
 * la liste complète des apps, où l'utilisateur choisit Nen à la main.
 */
private fun requestIgnoreBatteryOptimizations(context: Context) {
    val direct = Intent(
        Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
        Uri.parse("package:${context.packageName}")
    ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    try {
        context.startActivity(direct)
    } catch (e: ActivityNotFoundException) {
        try {
            context.startActivity(
                Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        } catch (e2: ActivityNotFoundException) {
            Toast.makeText(
                context,
                "Réglage introuvable : ouvrez Réglages > Batterie et retirez la restriction "
                    + "pour Nen.",
                Toast.LENGTH_LONG
            ).show()
        }
    }
}

/** Durée d'une révélation avant que le vœu ne se rescelle. */
private const val REVEAL_DURATION_MS = 10_000L

@Preview(showBackground = true, backgroundColor = 0xFF141414)
@Composable
fun ProtectionScreenPreview() {
    NenTheme {
        ProtectionScreen()
    }
}
