// Configure la résolution des plugins et des dépendances pour l'ensemble du projet.
pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    // Empêche les modules de déclarer leurs propres dépôts : tout passe par ceux-ci.
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

// Nom affiché du projet racine + déclaration du module applicatif "app".
rootProject.name = "GardeFou"
include(":app")
