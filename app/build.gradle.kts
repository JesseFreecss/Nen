// Build script du MODULE applicatif : ici on applique réellement les plugins
// et on configure l'app Android (SDK, ID, dépendances).
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)   // Compilateur Compose (obligatoire avec Kotlin 2.0+)
    alias(libs.plugins.ksp)              // Génère le code de Room à la compilation
}

android {
    // Espace de noms Kotlin/Java du module (sert aussi de base à la classe R).
    namespace = "com.jesse.gardefou"
    // Version du SDK utilisée pour COMPILER (API la plus récente stable).
    compileSdk = 35

    defaultConfig {
        applicationId = "com.jesse.gardefou"  // Identifiant unique de l'app sur l'appareil
        minSdk = 26                            // Android 8.0 minimum
        targetSdk = 35                         // Cible testée (comportement runtime)
        versionCode = 1                        // Entier incrémenté à chaque release
        versionName = "0.1.0"                  // Version lisible par l'humain

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            // Pas de minification pour l'instant (on itère vite). À activer plus tard.
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    // Cible le bytecode Java 17 (recommandé avec AGP 8.x).
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true   // Active Jetpack Compose pour ce module
    }
}

dependencies {
    // Bases Android + cycle de vie
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)

    // Compose via BOM : platform() applique les versions alignées du BOM à toutes les libs Compose.
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)

    // Room (persistance locale des mots-clés bloqués)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)   // ksp() = traitement d'annotations pour générer le code Room

    // Coroutines (boucle VPN asynchrone, accès DB non bloquant)
    implementation(libs.kotlinx.coroutines.android)

    // Outils Compose réservés au build de debug (aperçus @Preview dans Android Studio).
    debugImplementation(libs.androidx.ui.tooling)
}
