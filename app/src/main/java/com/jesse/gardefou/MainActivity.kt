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
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jesse.gardefou.accessibility.GardeFouAccessibilityService
import com.jesse.gardefou.blocklist.BlocklistSection
import com.jesse.gardefou.ui.theme.GardeFouTheme
import com.jesse.gardefou.vpn.GardeFouVpnService
import com.jesse.gardefou.vpn.ProtectionPrefs
import com.jesse.gardefou.vpn.VpnStateHolder

/**
 * Point d'entrée de l'application (activité de lancement).
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            GardeFouTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    ProtectionScreen(modifier = Modifier.padding(innerPadding))
                }
            }
        }
    }
}

/**
 * Écran principal : état de la protection, bouton d'activation (démarre/arrête le VPN),
 * et gestion de la liste de mots-clés bloqués.
 */
@Composable
fun ProtectionScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current

    // État marche/arrêt du VPN, observé depuis le service via VpnStateHolder.
    val isProtected by VpnStateHolder.running.collectAsStateWithLifecycle()

    // État "service d'accessibilité activé ?". Rafraîchi à chaque retour au premier plan
    // (ON_RESUME), notamment quand l'utilisateur revient des réglages système.
    var a11yEnabled by remember { mutableStateOf(isAccessibilityServiceEnabled(context)) }

    // État « app exclue des optimisations de batterie ? ». Sans cette exclusion, le système
    // finit par tuer le service VPN en arrière-plan et la protection se coupe toute seule.
    var batteryExempt by remember { mutableStateOf(isIgnoringBatteryOptimizations(context)) }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                a11yEnabled = isAccessibilityServiceEnabled(context)
                batteryExempt = isIgnoringBatteryOptimizations(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // Lanceur pour la boîte de dialogue système "Autoriser GardeFou à configurer un VPN ?".
    // Si l'utilisateur accepte (RESULT_OK), on démarre réellement le service.
    val vpnPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            GardeFouVpnService.start(context)
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
            GardeFouVpnService.start(context)
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

    // Action du bouton : bascule le VPN.
    fun onToggleProtection() {
        if (isProtected) {
            GardeFouVpnService.stop(context)
        } else {
            // prepare() renvoie un Intent si l'autorisation VPN n'a pas encore été donnée.
            val prepareIntent: Intent? = VpnService.prepare(context)
            if (prepareIntent != null) {
                vpnPermissionLauncher.launch(prepareIntent)
            } else {
                GardeFouVpnService.start(context)
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Top
    ) {
        Text(
            text = "GardeFou",
            style = MaterialTheme.typography.headlineLarge,
            modifier = Modifier.padding(top = 24.dp)
        )

        Button(
            onClick = { onToggleProtection() },
            modifier = Modifier.padding(top = 24.dp)
        ) {
            Text(
                text = if (isProtected) "Désactiver la protection"
                       else "Activer la protection"
            )
        }

        Text(
            text = if (isProtected) "Protection : activée"
                   else "Protection : désactivée",
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.SemiBold,
            color = if (isProtected) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.error,
            modifier = Modifier.padding(top = 16.dp)
        )

        // Surveillance in-app (service d'accessibilité) : état + accès aux réglages système.
        // Android ne permet pas d'activer ce service par programme : l'utilisateur doit le
        // faire manuellement. On l'y guide en ouvrant directement la page Accessibilité.
        Text(
            text = if (a11yEnabled) "Surveillance in-app : activée"
                   else "Surveillance in-app : désactivée",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = if (a11yEnabled) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.error,
            modifier = Modifier.padding(top = 24.dp)
        )

        if (!a11yEnabled) {
            Text(
                text = "Pour filtrer les URL dans les navigateurs et détecter les YouTube "
                       + "Shorts, activez « Protection GardeFou » dans les réglages "
                       + "d'accessibilité.",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 8.dp)
            )
            OutlinedButton(
                onClick = {
                    context.startActivity(
                        Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    )
                },
                modifier = Modifier.padding(top = 8.dp)
            ) {
                Text("Ouvrir les réglages d'accessibilité")
            }
        }

        // Optimisations de batterie : cause n°1 des coupures spontanées de la protection.
        // On n'affiche le bloc que si l'exclusion manque, pour ne pas encombrer l'écran.
        if (!batteryExempt) {
            Text(
                text = "Batterie : protection fragile",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(top = 24.dp)
            )
            Text(
                text = "Sans exclusion des optimisations de batterie, le système peut "
                       + "arrêter GardeFou en arrière-plan et la protection se coupe toute "
                       + "seule. Sur Xiaomi, pensez aussi à autoriser le « démarrage "
                       + "automatique » et à mettre l'économiseur de batterie sur « Aucune "
                       + "restriction » pour cette app.",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 8.dp)
            )
            OutlinedButton(
                onClick = { requestIgnoreBatteryOptimizations(context) },
                modifier = Modifier.padding(top = 8.dp)
            ) {
                Text("Exclure des optimisations de batterie")
            }
        }

        // Liste des mots-clés : prend la place restante (poids 1) pour son défilement interne.
        BlocklistSection(
            modifier = Modifier
                .weight(1f)
                .padding(top = 32.dp)
        )
    }
}

/**
 * Indique si le service d'accessibilité de GardeFou est actuellement activé par l'utilisateur.
 * On lit la liste système des services activés (Settings.Secure) et on y cherche notre composant.
 */
private fun isAccessibilityServiceEnabled(context: Context): Boolean {
    val expected = ComponentName(context, GardeFouAccessibilityService::class.java)
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
 * la liste complète des apps, où l'utilisateur choisit GardeFou à la main.
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
                    + "pour GardeFou.",
                Toast.LENGTH_LONG
            ).show()
        }
    }
}

@Preview(showBackground = true)
@Composable
fun ProtectionScreenPreview() {
    GardeFouTheme {
        ProtectionScreen()
    }
}
