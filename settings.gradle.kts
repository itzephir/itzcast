pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

val useMavenLocal = providers.gradleProperty("useMavenLocal")
    .map(String::toBooleanStrict)
    .orElse(false)

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        if (useMavenLocal.get()) {
            mavenLocal()
        }
        google()
        mavenCentral()
    }
}

rootProject.name = "itzcast"
includeBuild("build-logic")
include(":core", ":platform-desktop", ":extensions", ":app")
