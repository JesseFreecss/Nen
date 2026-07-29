# Nen

Application Android personnelle de contrôle de contenu : filtrage DNS par VPN local,
blocage de mots-clés dans les apps via un service d'accessibilité, écran de blocage
anti-contournement, vœux scellés et minuteur Pomodoro.

> Projet solo, non destiné au Play Store pour l'instant.

## Stack technique

- **Kotlin** + **Jetpack Compose** (Material 3), thème sombre uniquement
- **Room** pour la persistance des mots-clés, **coroutines** pour le VPN et la base
- **minSdk 26** (Android 8.0), **compileSdk/targetSdk 35**, bytecode Java 17
- Package : `com.jesse.nen`

Les numéros de version (AGP, Kotlin, Gradle, dépendances) sont centralisés dans
`gradle/libs.versions.toml` — c'est la source de vérité, pas ce README.

## Builder

Le SDK Android est installé sur cette machine (`local.properties` pointe dessus, non versionné).

Depuis **Android Studio** : ouvrir le dossier `Nen`, attendre le Gradle Sync, `Build > Make Project`.

En ligne de commande, `gradlew` a besoin d'un JDK 17+. Le plus simple est de réutiliser
celui embarqué dans Android Studio :

```powershell
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
.\gradlew assembleDebug   # génère l'APK debug
.\gradlew build           # compile + tests
```

### Appareil de test

Le téléphone de test est un **Xiaomi sous HyperOS**, qui tue agressivement les services
en arrière-plan. Toute modification touchant au VPN, au service d'accessibilité, au
Pomodoro ou à l'ambiance sonore doit être vérifiée sur l'appareil réel, pas seulement
sur émulateur : l'app peut fonctionner en apparence puis être coupée silencieusement
après quelques heures. C'est la raison d'être de `accessibility/A11yHeartbeat.kt` et de
la demande d'exclusion des optimisations de batterie.

## Écran d'accueil

Au lancement, une porte animée (`ui/NeteroGate.kt`) s'ouvre sur un champ d'orbes
flottantes posé sur un fond animé (`ui/CosmosBackground.kt`). Chaque orbe est un objet
manipulable à la main : les vœux scellés, la protection (Ten), le Pomodoro et l'ambiance
sonore. Les gestes (traîner, appui long pour sceller ou supprimer) sont captés une seule
fois pour tout le champ dans `orbs/OrbField.kt`.

## Structure des dossiers

```
com/jesse/nen/
├── MainActivity.kt     # assemblage de l'écran : porte, fond, champ d'orbes
├── orbs/               # moteur de simulation, rendu et gestes des orbes
├── ui/                 # fond animé, porte d'entrée, thème Material 3
├── blocklist/          # mots-clés bloqués : saisie, ViewModel, orbe de vœu, déverrouillage
├── data/               # Room : entité, DAO, base, repository
├── vpn/                # VPN local, parsing DNS, état de protection, relance au boot
├── accessibility/      # service d'accessibilité, overlay de blocage « aura », heartbeat
├── pomodoro/           # minuteur : service au premier plan, état, dialogue
├── sound/              # ambiance sonore en boucle (service mediaPlayback)
├── lockscreen/         # vide — le blocage est rendu par accessibility/AuraOverlay.kt
└── timer/              # vide — remplacé par pomodoro/
```

Les deux derniers dossiers ne contiennent qu'un `.gitkeep` hérité du découpage initial.

## Git

Dépôt privé `JesseFreecss/Nen`. Commits en français, à l'impératif ou au constat, sans
préfixe conventionnel imposé. Pousser sur `origin/main` en fin de session de travail.
