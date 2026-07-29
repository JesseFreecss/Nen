package com.jesse.nen

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
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.jesse.nen.accessibility.A11yHeartbeat
import com.jesse.nen.accessibility.NenAccessibilityService
import com.jesse.nen.blocklist.KeywordViewModel
import com.jesse.nen.blocklist.SealVowDialog
import com.jesse.nen.blocklist.VowUnlock
import com.jesse.nen.orbs.FaultKind
import com.jesse.nen.orbs.Orb
import com.jesse.nen.orbs.OrbEngine
import com.jesse.nen.orbs.OrbField
import com.jesse.nen.orbs.OrbKind
import com.jesse.nen.orbs.OrbSpec
import com.jesse.nen.pomodoro.PomodoroDialog
import com.jesse.nen.pomodoro.PomodoroStateHolder
import com.jesse.nen.sound.AmbiencePrefs
import com.jesse.nen.sound.AmbienceService
import com.jesse.nen.sound.AmbienceStateHolder
import com.jesse.nen.ui.CosmosBackground
import com.jesse.nen.ui.NeteroGate
import com.jesse.nen.ui.theme.NenTheme
import com.jesse.nen.vpn.NenVpnService
import com.jesse.nen.vpn.ProtectionPrefs
import com.jesse.nen.vpn.VpnStateHolder
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

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
                    NenScreen()
                }
            }
        }
    }
}

/**
 * L'écran unique de l'app : un champ d'orbes qui flottent devant l'anneau iridescent.
 *
 * Il n'y a rien d'autre — ni titre, ni carte, ni bouton. Chaque fonction est une orbe, et
 * tout passe par trois gestes : toucher pour agir, traîner pour déplacer et lancer, maintenir
 * puis relâcher pour ouvrir un menu.
 */
@Composable
fun NenScreen(keywordViewModel: KeywordViewModel = viewModel()) {
    val context = LocalContext.current
    val density = LocalDensity.current

    val isProtected by VpnStateHolder.running.collectAsStateWithLifecycle()
    val keywords by keywordViewModel.keywords.collectAsStateWithLifecycle()
    val pomodoro by PomodoroStateHolder.state.collectAsStateWithLifecycle()

    val soundPlaying by AmbienceStateHolder.playing.collectAsStateWithLifecycle()

    var showSealDialog by remember { mutableStateOf(false) }
    var showPomodoro by remember { mutableStateOf(false) }
    var showVolume by remember { mutableStateOf(false) }
    var shownFault by remember { mutableStateOf<FaultKind?>(null) }

    // Menus contextuels de l'appui long, ancrés à l'endroit du geste.
    var sealMenuAt by remember { mutableStateOf<Offset?>(null) }
    var vowMenuFor by remember { mutableStateOf<Orb?>(null) }
    var soundMenuFor by remember { mutableStateOf<Orb?>(null) }

    // Sélecteur de morceau. OpenDocument et non GetContent : lui seul donne une autorisation
    // d'accès durable, sans quoi le morceau deviendrait illisible au prochain démarrage.
    val trackPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            context.contentResolver.takePersistableUriPermission(
                uri, Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
            AmbiencePrefs.setTrackUri(context, uri)
            AmbienceService.send(context, AmbienceService.ACTION_PLAY)
        }
    }

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
                Toast.makeText(context, "Déverrouillage impossible : $message", Toast.LENGTH_LONG)
                    .show()
            }
        )
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

    // Sans exclusion des optimisations de batterie, le système finit par tuer le service VPN
    // en arrière-plan et la protection se coupe toute seule.
    var batteryExempt by remember { mutableStateOf(isIgnoringBatteryOptimizations(context)) }
    var batteryMuted by remember { mutableStateOf(ProtectionPrefs.isBatteryWarningMuted(context)) }

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
    val vpnPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            NenVpnService.start(context)
        }
    }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* accordée ou non : le service fonctionne dans les deux cas */ }

    // Reprise après un arrêt subi : si l'utilisateur avait laissé la protection active mais
    // que le VPN ne tourne pas (service tué par le système), on le remonte dès l'ouverture.
    // Sans autorisation VPN encore valide on ne fait rien : ce serait ouvrir une boîte de
    // dialogue sans que l'utilisateur ait rien demandé.
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

    fun toggleProtection() {
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

    // Les failles en cours : une orbe rouge par réglage manquant, qui s'efface une fois réglé.
    val faults = buildList {
        if (!a11yEnabled) add(FaultKind.ACCESSIBILITY_OFF)
        if (a11yEnabled && !a11yAlive) add(FaultKind.ACCESSIBILITY_DEAD)
        if (!batteryExempt && !batteryMuted) add(FaultKind.BATTERY)
    }

    val engine = remember { OrbEngine() }
    val specs = remember(keywords, faults, density) {
        buildList {
            add(spec("ten", OrbKind.Ten, TEN_BOX, density.density))
            add(spec("pomodoro", OrbKind.Pomodoro, POMODORO_BOX, density.density))
            add(spec("sound", OrbKind.Sound, SOUND_BOX, density.density))
            faults.forEach { fault ->
                add(spec("fault:${fault.name}", OrbKind.Fault(fault), FAULT_BOX, density.density))
            }
            keywords.take(MAX_VOW_ORBS).forEach { vow ->
                add(spec("vow:${vow.id}", OrbKind.Vow(vow.id), VOW_BOX, density.density))
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        // Le fond va d'un bord à l'autre, mais les orbes restent dans la zone sûre : une orbe
        // posée sous la barre de navigation serait injoignable.
        CosmosBackground()

        OrbField(
            modifier = Modifier.windowInsetsPadding(WindowInsets.systemBars),
            engine = engine,
            specs = specs,
            tenActive = isProtected,
            pomodoroActive = pomodoro.running && !pomodoro.paused,
            soundPlaying = soundPlaying,
            revealedVowId = revealedVowId,
            revealedKeyword = keywords.firstOrNull { it.id == revealedVowId }?.keyword,
            onTap = { orb ->
                when (val kind = orb.kind) {
                    is OrbKind.Ten -> toggleProtection()
                    is OrbKind.Pomodoro -> showPomodoro = true
                    is OrbKind.Fault -> shownFault = kind.fault
                    is OrbKind.Vow -> requestReveal(kind.id)
                    is OrbKind.Sound -> when {
                        soundPlaying -> AmbienceService.send(context, AmbienceService.ACTION_STOP)
                        AmbiencePrefs.trackUri(context) == null -> trackPicker.launch(AUDIO_MIME)
                        else -> AmbienceService.send(context, AmbienceService.ACTION_PLAY)
                    }
                }
            },
            onLongPressOrb = { orb ->
                when (orb.kind) {
                    is OrbKind.Vow -> vowMenuFor = orb
                    is OrbKind.Sound -> soundMenuFor = orb
                    else -> Unit
                }
            },
            onLongPressBackground = { position -> sealMenuAt = position }
        )

        // Les menus n'ont pas d'élément d'interface auquel s'accrocher : on ancre une boîte
        // de taille nulle à l'endroit exact du geste.
        sealMenuAt?.let { position ->
            MenuAnchor(position) {
                DropdownMenu(expanded = true, onDismissRequest = { sealMenuAt = null }) {
                    DropdownMenuItem(
                        text = { Text("Sceller un vœu") },
                        onClick = {
                            sealMenuAt = null
                            showSealDialog = true
                        }
                    )
                }
            }
        }

        vowMenuFor?.let { orb ->
            MenuAnchor(Offset(orb.x, orb.y)) {
                DropdownMenu(expanded = true, onDismissRequest = { vowMenuFor = null }) {
                    DropdownMenuItem(
                        text = { Text("Supprimer", color = MaterialTheme.colorScheme.error) },
                        onClick = {
                            val id = (orb.kind as OrbKind.Vow).id
                            vowMenuFor = null
                            keywords.firstOrNull { it.id == id }?.let(keywordViewModel::remove)
                        }
                    )
                }
            }
        }

        soundMenuFor?.let { orb ->
            MenuAnchor(Offset(orb.x, orb.y)) {
                DropdownMenu(expanded = true, onDismissRequest = { soundMenuFor = null }) {
                    DropdownMenuItem(
                        text = { Text("Choisir un morceau") },
                        onClick = {
                            soundMenuFor = null
                            trackPicker.launch(AUDIO_MIME)
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Volume") },
                        onClick = {
                            soundMenuFor = null
                            showVolume = true
                        }
                    )
                    if (AmbiencePrefs.trackUri(context) != null) {
                        DropdownMenuItem(
                            text = {
                                Text("Retirer le morceau", color = MaterialTheme.colorScheme.error)
                            },
                            onClick = {
                                soundMenuFor = null
                                AmbienceService.send(context, AmbienceService.ACTION_STOP)
                                AmbiencePrefs.setTrackUri(context, null)
                            }
                        )
                    }
                }
            }
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

    if (showPomodoro) {
        PomodoroDialog(state = pomodoro, onDismiss = { showPomodoro = false })
    }

    if (showVolume) {
        VolumeDialog(
            initial = AmbiencePrefs.volume(context),
            onChange = { value ->
                AmbiencePrefs.setVolume(context, value)
                // Sans lecture en cours, le service n'a rien à ajuster : il se rendormirait
                // aussitôt. Le nouveau volume sera pris au prochain démarrage.
                if (soundPlaying) {
                    AmbienceService.send(context, AmbienceService.ACTION_SET_VOLUME)
                }
            },
            onDismiss = { showVolume = false }
        )
    }

    shownFault?.let { fault ->
        FaultDialog(
            fault = fault,
            onDismiss = { shownFault = null },
            onMute = if (fault == FaultKind.BATTERY) {
                {
                    ProtectionPrefs.setBatteryWarningMuted(context, true)
                    batteryMuted = true
                    shownFault = null
                }
            } else {
                null
            },
            onAction = {
                shownFault = null
                when (fault) {
                    FaultKind.BATTERY -> requestIgnoreBatteryOptimizations(context)
                    else -> context.startActivity(
                        Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    )
                }
            }
        )
    }
}

/** Réglage du volume de l'ambiance. */
@Composable
private fun VolumeDialog(initial: Float, onChange: (Float) -> Unit, onDismiss: () -> Unit) {
    var value by remember { mutableFloatStateOf(initial) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(16.dp),
        title = {
            Text(
                text = "Volume de l'ambiance",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
        },
        text = {
            Slider(
                value = value,
                onValueChange = {
                    value = it
                    onChange(it)
                }
            )
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Fermer") }
        }
    )
}

/** Boîte de taille nulle posée à [position], pour donner un point d'ancrage à un menu. */
@Composable
private fun MenuAnchor(position: Offset, content: @Composable () -> Unit) {
    Box(
        modifier = Modifier.offset {
            IntOffset(position.x.roundToInt(), position.y.roundToInt())
        }
    ) {
        content()
    }
}

/**
 * Ce qui manque, pourquoi c'est un problème, et le réglage qui le corrige.
 *
 * [onMute], quand il est fourni, permet de faire taire l'alerte définitivement : la demande
 * système d'exclusion de batterie reste sans effet sur certaines surcouches, et sans cette
 * porte de sortie l'orbe rouge resterait à l'écran quoi que fasse l'utilisateur.
 */
@Composable
private fun FaultDialog(
    fault: FaultKind,
    onDismiss: () -> Unit,
    onAction: () -> Unit,
    onMute: (() -> Unit)? = null
) {
    val title: String
    val message: String
    val actionLabel: String
    when (fault) {
        FaultKind.ACCESSIBILITY_OFF -> {
            title = "Faille : surveillance in-app"
            message = "Pour filtrer les URL dans les navigateurs et détecter les YouTube " +
                "Shorts, activez « Protection Nen » dans les réglages d'accessibilité."
            actionLabel = "Ouvrir les réglages"
        }
        FaultKind.ACCESSIBILITY_DEAD -> {
            title = "Faille : surveillance interrompue"
            message = "« Protection Nen » est activée dans les réglages, mais le service ne " +
                "répond plus : le système l'a arrêté. Rien n'est surveillé pour le moment. " +
                "Désactivez puis réactivez-la pour la relancer."
            actionLabel = "Ouvrir les réglages"
        }
        FaultKind.BATTERY -> {
            title = "Faille : batterie"
            message = "Sans exclusion des optimisations de batterie, le système peut arrêter " +
                "Nen en arrière-plan et la protection se coupe toute seule.\n\n" +
                "Sur Xiaomi, le bouton ci-dessous n'ouvre pas la demande d'autorisation " +
                "d'Android : HyperOS le détourne vers son propre écran « Détails de la " +
                "batterie », qui ne peut pas accorder cette exclusion. Il n'y a alors rien à " +
                "y faire de plus.\n\n" +
                "Ce qui protège réellement Nen sur cet appareil : « Pas de restriction » dans " +
                "cet écran Xiaomi, et le « démarrage automatique » autorisé. Une fois les " +
                "deux faits, faites taire cette alerte — Android continuera de la signaler " +
                "sans jamais pouvoir être satisfait."
            actionLabel = "Ouvrir les réglages batterie"
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(16.dp),
        title = {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.error
            )
        },
        text = {
            Column {
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (onMute != null) {
                    TextButton(onClick = onMute) {
                        Text(
                            text = "Ne plus signaler",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onAction) { Text(actionLabel) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Plus tard", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    )
}

/**
 * Toutes les orbes suivent la même convention : la sphère occupe 0,30 de sa boîte de dessin,
 * le reste étant la marge où s'éteint le halo. Le rayon de collision est donc celui de la
 * sphère visible, halo exclu — deux halos peuvent se chevaucher, deux sphères non.
 */
private fun spec(key: String, kind: OrbKind, box: Dp, densityScale: Float): OrbSpec {
    val boxPx = box.value * densityScale
    return OrbSpec(key = key, kind = kind, radius = boxPx * 0.30f, boxSize = boxPx)
}

/** Types MIME acceptés par le sélecteur de morceau. */
private val AUDIO_MIME = arrayOf("audio/*")

private val TEN_BOX = 52.dp
private val POMODORO_BOX = 31.dp
private val SOUND_BOX = 31.dp
private val FAULT_BOX = 29.dp
private val VOW_BOX = 31.dp

/**
 * Après un import de liste hosts, la base peut compter des dizaines de milliers d'entrées :
 * on ne fait flotter que les premières. Au-delà, ni l'écran ni la boucle de collisions ne
 * suivraient.
 */
private const val MAX_VOW_ORBS = 40

/** Durée d'une révélation avant que le vœu ne se rescelle. */
private const val REVEAL_DURATION_MS = 10_000L

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
