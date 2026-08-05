pluginManagement {
    repositories {
        maven("https://maven.neoforged.net/releases")
        mavenLocal()
        gradlePluginPortal()
        maven("https://maven.fabricmc.net/") {
            name = "Fabric"
        }
        maven("https://repo.spongepowered.org/repository/maven-public/") {
            name = "Sponge Snapshots"
        }
        maven("https://raw.githubusercontent.com/Fuzss/modresources/main/maven/") {
            name = "Fuzs Mod Resources"
        }
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "0.9.0"
}

rootProject.name = "StorageDrawers"
include("common")
// :forge stays out. ForgeGradle 6 (which tops out at 6.0.54) refuses to apply on
// Gradle 9 — "Versions Gradle 9.0 and newer are not supported yet" — so including it
// fails CONFIGURATION, which takes :neoforge and :fabric down with it. ForgeGradle 7
// applies but its Minecraft-dependency wiring is undocumented and produced an empty
// compile classpath in testing. Re-enable only once that is solved.
//include("forge")
include("neoforge")
include("fabric")