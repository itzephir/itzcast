plugins {
    alias(libs.plugins.kotlin.multiplatform) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.compose) apply false
    alias(libs.plugins.kotlin.compose) apply false
}

allprojects {
    group = "dev.itzcast"
    version = providers.gradleProperty("itzcast.version").get()
}
