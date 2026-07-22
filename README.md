# GardeFou

Application Android personnelle de contrôle de contenu (blocage de mots-clés,
blocage des YouTube Shorts, écran de verrouillage anti-contournement).

> Projet solo, non destiné au Play Store pour l'instant.

## Stack technique

| Élément        | Version / Choix                        |
|----------------|----------------------------------------|
| Langage        | Kotlin                                 |
| UI             | Jetpack Compose (Material 3)           |
| minSdk         | 26 (Android 8.0)                       |
| compileSdk/target | 35 (Android 15)                     |
| AGP            | 8.7.3                                  |
| Kotlin         | 2.0.21                                 |
| Gradle         | 8.9                                    |
| Package        | `com.jesse.gardefou`                   |

## Ouvrir et builder

Cette machine n'ayant pas le SDK Android installé, le plus simple est **Android Studio** :

1. Ouvrir le dossier `GardeFou` dans Android Studio (Giraffe/Koala ou plus récent).
2. Laisser Android Studio installer le SDK manquant et générer `local.properties`
   (fichier local pointant vers le SDK, non versionné).
3. Attendre le **Gradle Sync**, puis `Build > Make Project`.
4. Lancer sur un émulateur ou un appareil **API 26+** :
   écran « GardeFou » + bouton « Activer la protection » + « Protection : désactivée ».

### En ligne de commande (une fois le SDK installé)

```bash
./gradlew build        # compile + tests
./gradlew assembleDebug # génère l'APK debug
```

## Structure des dossiers (packages à remplir)

```
com/jesse/gardefou/
├── MainActivity.kt        # écran d'accueil Compose
├── ui/theme/              # thème Material 3 (couleurs, typo)
├── vpn/                   # (à venir) interception réseau / filtrage
├── accessibility/         # (à venir) service d'accessibilité (blocage in-app)
├── blocklist/             # (à venir) gestion des mots-clés bloqués
├── lockscreen/            # (à venir) écran de verrouillage anti-contournement
├── timer/                 # (à venir) minuteries / délais
└── data/                  # (à venir) persistance (DataStore/Room)
```

Chaque dossier « à venir » contient un `.gitkeep` pour être conservé par Git tant
qu'il est vide.
