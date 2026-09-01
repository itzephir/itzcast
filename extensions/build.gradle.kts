import org.gradle.api.file.DuplicatesStrategy
import org.gradle.jvm.tasks.Jar

plugins {
    id("itzcast.kotlin-jvm-convention")
    alias(libs.plugins.kotlin.serialization)
}

val calktVersion = providers.gradleProperty("calktVersion")
    .orElse(libs.versions.calkt)

dependencies {
    implementation(project(":core"))
    implementation(libs.coroutines.core)
    implementation(libs.serialization.json)
    implementation(libs.calkt.math) {
        version {
            require(calktVersion.get())
        }
    }
    testImplementation(libs.coroutines.test)
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
