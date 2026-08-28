plugins {
    id("itzcast.kotlin-jvm-convention")
    alias(libs.plugins.kotlin.serialization)
}

dependencies {
    implementation(project(":core"))
    implementation(libs.coroutines.core)
    implementation(libs.serialization.json)
    implementation(libs.ktoml.core)
    implementation(libs.jna)
    testImplementation(libs.coroutines.test)
}
