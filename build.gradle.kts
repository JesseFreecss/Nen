// Build script RACINE : ne fait que déclarer les plugins disponibles pour les modules.
// `apply false` = on rend le plugin connu du projet, mais on ne l'applique pas ici
// (il sera appliqué dans app/build.gradle.kts). Les versions viennent du catalogue libs.versions.toml.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.ksp) apply false
}
