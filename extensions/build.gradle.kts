import org.gradle.api.file.DuplicatesStrategy
import org.gradle.jvm.tasks.Jar

plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
}

kotlin {
    jvmToolchain(21)
}

dependencies {
    implementation(project(":core"))
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.8.1")
    testImplementation(kotlin("test-junit5"))
}

tasks.test {
    useJUnitPlatform()
}

val extensionRuntimeJar by tasks.registering(Jar::class) {
    group = "distribution"
    archiveFileName.set("itzcast-extensions.jar")
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    manifest.attributes["Main-Class"] = "dev.itzcast.extensions.MainKt"
    from(sourceSets.main.get().output)
    dependsOn(configurations.runtimeClasspath)
    from({
        configurations.runtimeClasspath.get().map { dependency ->
            if (dependency.isDirectory) dependency else zipTree(dependency)
        }
    })
}

tasks.register<Zip>("bundledExtensionArchive") {
    group = "distribution"
    archiveFileName.set("bundled-extensions.zip")
    destinationDirectory.set(layout.buildDirectory.dir("bundled"))
    from(layout.projectDirectory.dir("catalog"))
    from(extensionRuntimeJar) {
        into(".runtime")
    }
}
