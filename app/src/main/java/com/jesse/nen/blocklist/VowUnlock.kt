package com.jesse.nen.blocklist

import android.content.Context
import android.os.Build
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity

/**
 * Déverrouillage d'un vœu scellé par empreinte digitale.
 *
 * Repli sur le code de déverrouillage du téléphone si aucune empreinte n'est enregistrée :
 * sans lui, un utilisateur sans capteur ou sans empreinte configurée ne pourrait plus jamais
 * consulter sa propre liste.
 */
object VowUnlock {

    /** Ce que l'appareil sait faire, pour choisir le type d'invite. */
    private fun biometricsReady(context: Context): Boolean =
        BiometricManager.from(context).canAuthenticate(Authenticators.BIOMETRIC_STRONG) ==
            BiometricManager.BIOMETRIC_SUCCESS

    /**
     * Demande une authentification. [onSuccess] n'est appelé que sur succès avéré ; une
     * annulation ou un échec laisse le vœu scellé.
     */
    fun prompt(
        activity: FragmentActivity,
        onSuccess: () -> Unit,
        onUnavailable: (CharSequence) -> Unit
    ) {
        val executor = ContextCompat.getMainExecutor(activity)
        val prompt = BiometricPrompt(
            activity,
            executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    onSuccess()
                }

                override fun onAuthenticationError(code: Int, message: CharSequence) {
                    // L'annulation volontaire n'est pas une anomalie : on ne dit rien.
                    if (code != BiometricPrompt.ERROR_USER_CANCELED &&
                        code != BiometricPrompt.ERROR_NEGATIVE_BUTTON &&
                        code != BiometricPrompt.ERROR_CANCELED
                    ) {
                        onUnavailable(message)
                    }
                }
            }
        )

        val info = if (biometricsReady(activity)) {
            BiometricPrompt.PromptInfo.Builder()
                .setTitle("Sceau du grimoire")
                .setSubtitle("Pose ton doigt pour révéler ce vœu")
                .setNegativeButtonText("Annuler")
                .setAllowedAuthenticators(Authenticators.BIOMETRIC_STRONG)
                .build()
        } else {
            // Sans empreinte utilisable, on demande le code de l'appareil. Le bouton négatif
            // est interdit avec DEVICE_CREDENTIAL, l'invite système gère elle-même l'abandon.
            BiometricPrompt.PromptInfo.Builder()
                .setTitle("Sceau du grimoire")
                .setSubtitle("Déverrouille pour révéler ce vœu")
                .apply {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        setAllowedAuthenticators(Authenticators.DEVICE_CREDENTIAL)
                    } else {
                        // Avant Android 11, DEVICE_CREDENTIAL seul n'est pas accepté par
                        // setAllowedAuthenticators ; c'est l'ancienne API qui s'applique.
                        @Suppress("DEPRECATION")
                        setDeviceCredentialAllowed(true)
                    }
                }
                .build()
        }

        prompt.authenticate(info)
    }
}
