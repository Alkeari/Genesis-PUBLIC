pluginManagement {
    // Modtrix is built from source: the nearest folder named Modtrix beside this project or one of its parents.
    includeBuild(generateSequence(settingsDir) { it.parentFile }.map { it.resolve("Modtrix") }.first { it.resolve("settings.gradle.kts").isFile }.path)
    repositories {
        gradlePluginPortal()
        mavenCentral()
        maven("https://maven.fabricmc.net/")
        maven("https://maven.neoforged.net/releases/")
        maven("https://maven.kikugie.dev/releases")
    }
}

plugins {
    id("com.alkeari.modtrix")
}

rootProject.name = "Genesis"
