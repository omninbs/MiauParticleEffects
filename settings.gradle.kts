pluginManagement {
    repositories {
        mavenCentral()
        gradlePluginPortal()
        maven("https://maven.fabricmc.net/")
        maven("https://maven.kikugie.dev/releases") { name = "KikuGie Releases" }
        maven("https://maven.kikugie.dev/snapshots") { name = "KikuGie Snapshots" }
    }
}

plugins {
    id("dev.kikugie.stonecutter") version "0.9.8"

    // Cross-compat for 26.1+ (unobfuscated) and older (obfuscated) versions.
    // See https://codeberg.org/KikuGie/loom-back-compat
    id("dev.kikugie.loom-back-compat") version "0.4.2"

    // Auto-downloads the Java toolchain needed for each Minecraft version.
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

stonecutter {
    create(rootProject) {
        versions("1.21.1", "1.21.4", "1.21.8", "1.21.11", "26.2")
        vcsVersion = "26.2"
    }
}

rootProject.name = "MiauParticleEffects"