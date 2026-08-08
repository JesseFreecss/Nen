package com.jesse.nen

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.net.VpnService
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.jesse.nen.accessibility.A11yHeartbeat
import com.jesse.nen.accessibility.ShortFormDialog
import com.jesse.nen.blocklist.KeywordViewModel
import com.jesse.nen.blocklist.SermentDialog
import com.jesse.nen.blocklist.VowUnlock
import com.jesse.nen.common.isAccessibilityServiceEnabled
import com.jesse.nen.common.isIgnoringBatteryOptimizations
import com.jesse.nen.common.requestIgnoreBatteryOptimizations
import com.jesse.nen.orbs.FaultDialog
import com.jesse.nen.orbs.FaultKind
import com.jesse.nen.orbs.OrbEngine
import com.jesse.nen.orbs.OrbField
import com.jesse.nen.orbs.OrbKind
import com.jesse.nen.orbs.OrbSpec
import com.jesse.nen.pomodoro.PomodoroDialog
import com.jesse.nen.pomodoro.PomodoroStateHolder
import com.jesse.nen.sound.AmbienceLibraryDialog
import com.jesse.nen.sound.AmbiencePrefs
import com.jesse.nen.sound.AmbienceService
import com.jesse.nen.sound.AmbienceStateHolder
import com.jesse.nen.sound.AmbienceViewModel
import com.jesse.nen.ui.CosmosBackground
import com.jesse.nen.ui.NeteroGate
import com.jesse.nen.ui.theme.NenTheme
import com.jesse.nen.vpn.NenVpnService
import com.jesse.nen.vpn.ProtectionPrefs
import com.jesse.nen.vpn.VpnStateHolder
import kotlinx.coroutines.delay

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
fun NenScreen(
    keywordViewModel: KeywordViewModel = viewModel(),
    ambienceViewModel: AmbienceViewModel = viewModel()
) {
    val context = LocalContext.current
    val density = LocalDensity.current

    val isProtected by VpnStateHolder.running.collectAsStateWithLifecycle()
    val keywords by keywordViewModel.keywords.collectAsStateWithLifecycle()
    val pomodoro by PomodoroStateHolder.state.collectAsStateWithLifecycle()

    val soundPlaying by AmbienceStateHolder.playing.collectAsStateWithLifecycle()
    val ambienceTracks by ambienceViewModel.tracks.collectAsStateWithLifecycle()

    var showSermentDialog by remember { mutableStateOf(false) }
    var showPomodoro by remember { mutableStateOf(false) }
    var showAmbienceLibrary by remember { mutableStateOf(false) }
    var showShortFormDialog by remember { mutableStateOf(false) }
    var shownFault by remember { mutableStateOf<FaultKind?>(null) }

    // Sélecteur de morceau. OpenDocument et non GetContent : lui seul donne une autorisation
    // d'accès durable, sans quoi le morceau deviendrait illisible au prochain démarrage.
    val trackPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            context.contentResolver.takePersistableUriPermission(
                uri, Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
            ambienceViewModel.add(uri)
            AmbiencePrefs.setTrackUri(context, uri)
            AmbienceService.send(context, AmbienceService.ACTION_PLAY)
        }
    }

    // BiometricPrompt s'accroche à l'activité hôte, pas au contexte Compose.
    val hostActivity = context as? FragmentActivity

    fun requestSermentAccess() {
        val activity = hostActivity ?: return
        VowUnlock.prompt(
            activity = activity,
            onSuccess = { showSermentDialog = true },
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

    // Protection voulue mais pas en cours : soit une reprise après un arrêt subi
    // (l'utilisateur avait laissé la protection active mais le VPN ne tourne pas — service
    // tué par le système), soit une toute première ouverture (ProtectionPrefs.isEnabled est
    // vraie par défaut). Dans les deux cas on la remonte dès l'ouverture ; sans autorisation
    // VPN encore valide, on déclenche nous-mêmes la boîte système au lieu d'attendre que
    // l'utilisateur pense à taper sur l'orbe Ten.
    LaunchedEffect(isProtected) {
        if (!isProtected && ProtectionPrefs.isEnabled(context)) {
            val prepareIntent = VpnService.prepare(context)
            if (prepareIntent == null) {
                NenVpnService.start(context)
            } else {
                vpnPermissionLauncher.launch(prepareIntent)
            }
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

    // La protection est activée par défaut et volontairement difficile à couper : un tap sur
    // l'orbe Ten pendant qu'elle tourne ne fait qu'ouvrir l'invite d'empreinte, jamais l'arrêt
    // direct — sans quoi n'importe qui pourrait la désactiver d'un geste.
    fun requestDisableProtection() {
        val activity = hostActivity ?: return
        VowUnlock.prompt(
            activity = activity,
            title = "Protection Nen",
            subtitle = "Pose ton doigt pour désactiver la protection",
            deviceCredentialSubtitle = "Déverrouille pour désactiver la protection",
            onSuccess = { NenVpnService.stop(context) },
            onUnavailable = { message ->
                Toast.makeText(context, "Déverrouillage impossible : $message", Toast.LENGTH_LONG)
                    .show()
            }
        )
    }

    fun toggleProtection() {
        if (isProtected) {
            requestDisableProtection()
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
    val specs = remember(faults, density) {
        buildList {
            add(spec("ten", OrbKind.Ten, TEN_BOX, density.density))
            add(spec("pomodoro", OrbKind.Pomodoro, POMODORO_BOX, density.density))
            add(spec("sound", OrbKind.Sound, SOUND_BOX, density.density))
            faults.forEach { fault ->
                add(spec("fault:${fault.name}", OrbKind.Fault(fault), FAULT_BOX, density.density))
            }
            add(spec("serment", OrbKind.Serment, SERMENT_BOX, density.density))
            add(spec("shortform", OrbKind.ShortForm, SHORTFORM_BOX, density.density))
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
            onTap = { orb ->
                when (val kind = orb.kind) {
                    is OrbKind.Ten -> toggleProtection()
                    is OrbKind.Pomodoro -> showPomodoro = true
                    is OrbKind.Fault -> shownFault = kind.fault
                    is OrbKind.Serment -> requestSermentAccess()
                    is OrbKind.Sound -> showAmbienceLibrary = true
                    is OrbKind.ShortForm -> showShortFormDialog = true
                }
            },
            onLongPressOrb = {},
            onLongPressBackground = {}
        )
    }

    if (showSermentDialog) {
        SermentDialog(
            keywords = keywords,
            onAdd = keywordViewModel::add,
            onRemove = keywordViewModel::remove,
            onDismiss = { showSermentDialog = false }
        )
    }

    if (showPomodoro) {
        PomodoroDialog(state = pomodoro, onDismiss = { showPomodoro = false })
    }

    if (showShortFormDialog) {
        ShortFormDialog(onDismiss = { showShortFormDialog = false })
    }

    if (showAmbienceLibrary) {
        AmbienceLibraryDialog(
            tracks = ambienceTracks,
            activeUri = AmbiencePrefs.trackUri(context)?.toString(),
            playing = soundPlaying,
            volume = AmbiencePrefs.volume(context),
            onSelect = { track ->
                AmbiencePrefs.setTrackUri(context, Uri.parse(track.uri))
                AmbienceService.send(context, AmbienceService.ACTION_PLAY)
            },
            onToggleActive = {
                if (soundPlaying) {
                    AmbienceService.send(context, AmbienceService.ACTION_STOP)
                } else {
                    AmbienceService.send(context, AmbienceService.ACTION_PLAY)
                }
            },
            onRemove = { track ->
                ambienceViewModel.remove(track)
                // Le morceau retiré était celui en cours : on arrête, sinon l'orbe continuerait
                // de jouer un fichier qui vient de disparaître de la bibliothèque.
                if (track.uri == AmbiencePrefs.trackUri(context)?.toString()) {
                    AmbienceService.send(context, AmbienceService.ACTION_STOP)
                    AmbiencePrefs.setTrackUri(context, null)
                }
            },
            onAddRequested = { trackPicker.launch(AUDIO_MIME) },
            onVolumeChange = { value ->
                AmbiencePrefs.setVolume(context, value)
                // Sans lecture en cours, le service n'a rien à ajuster : il se rendormirait
                // aussitôt. Le nouveau volume sera pris au prochain démarrage.
                if (soundPlaying) {
                    AmbienceService.send(context, AmbienceService.ACTION_SET_VOLUME)
                }
            },
            onDismiss = { showAmbienceLibrary = false }
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
private val SERMENT_BOX = 34.dp
private val SHORTFORM_BOX = 31.dp
