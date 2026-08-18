plugins {
    kotlin("jvm")
    id("org.jetbrains.compose")
    id("org.jetbrains.kotlin.plugin.compose")
}

evaluationDependsOn(":extensions")
val bundledExtensionArchive = project(":extensions").tasks.named<Zip>("bundledExtensionArchive")

val releaseVersion = project.version.toString()
val macPackageVersion = releaseVersion
    .split('.')
    .let { parts -> if (parts.firstOrNull() == "0") listOf("1") + parts.drop(1) else parts }
    .joinToString(".")

kotlin {
    jvmToolchain(21)
}

dependencies {
    implementation(project(":core"))
    implementation(project(":platform-desktop"))
    implementation(compose.desktop.currentOs)
    implementation("org.jetbrains.compose.material:material:1.11.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")
    testImplementation(kotlin("test-junit5"))
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.2")
}

tasks.test {
    useJUnitPlatform()
}

tasks.named<Copy>("processResources") {
    dependsOn(bundledExtensionArchive)
    from(bundledExtensionArchive.flatMap { it.archiveFile }) {
        into("itzcast")
    }
    from(rootProject.file("LICENSE")) {
        into("META-INF")
        rename { "LICENSE.txt" }
    }
    from(rootProject.file("NOTICE")) {
        into("META-INF")
        rename { "NOTICE.txt" }
    }
}

compose.desktop {
    application {
        mainClass = "dev.itzcast.app.MainKt"
        jvmArgs += "-Ditzcast.version=$releaseVersion"
        nativeDistributions {
            targetFormats(org.jetbrains.compose.desktop.application.dsl.TargetFormat.Dmg)
            packageName = "itzcast"
            // jpackage rejects macOS app versions starting with zero. The public release
            // version remains 0.0.1; only the technical CFBundle version is normalized.
            packageVersion = macPackageVersion
            description = "An extensible command launcher"
            vendor = "itzcast"
            macOS {
                bundleID = "dev.itzcast.app"
                dockName = "itzcast"
            }
        }
    }
}

tasks.register<Sync>("packagePublicDmg") {
    group = "distribution"
    description = "Builds a DMG named with the public itzcast release version."
    dependsOn("packageDmg")
    from(layout.buildDirectory.file("compose/binaries/main/dmg/itzcast-$macPackageVersion.dmg"))
    into(layout.buildDirectory.dir("release"))
    rename { "itzcast-$releaseVersion.dmg" }
}
