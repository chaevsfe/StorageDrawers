package com.texelsaurus;

object Versions {
    const val mod = "19.1.3"
    const val java = "25"
    const val minecraft = "26.1"
    const val minecraftRange = "[26.1,26.2)"
    const val minecraftLower = "26.1"
    const val minecraftUpper = "26.2"

    // Forge/NeoForge are commented out of settings.gradle.kts for the Fabric port.
    // Recorded here for the eventual re-enable; NOT build-verified — confirm against
    // the mavens before trusting them.
    const val forge = "26.2-65.0.7"
    const val forgeVersionRange = "[65,)"
    const val forgeLoaderRange = "[65,)"
    const val neoForge = "26.2.0.28-beta"
    const val neoForgeVersionRange = "[26.2.0-beta,)"
    const val neoForgeLoaderRange = "[4,)"

    const val fabric = "0.145.1+26.1"
    const val fabricLoaderMin = "0.19.3"
    const val fabricLoader = "0.19.3"
}
