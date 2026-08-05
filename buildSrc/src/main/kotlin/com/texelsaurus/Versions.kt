package com.texelsaurus;

object Versions {
    const val mod = "19.1.5"
    const val java = "25"
    const val minecraft = "26.1"
    const val minecraftRange = "[26.1,26.2)"
    const val minecraftLower = "26.1"
    const val minecraftUpper = "26.2"

    // :forge is still commented out of settings.gradle.kts — ForgeGradle 6 cannot run on
    // Gradle 9. These coordinates are recorded for that eventual re-enable and are NOT
    // build-verified; note the interpolation is "${minecraft}-${forge}", so this holds the
    // build number only. The 26.1 line is 62.x/63.x/64.x (26.1 -> 62.0.9, 26.1.2 -> 64.1.0).
    const val forge = "62.0.9"
    const val forgeVersionRange = "[62,)"
    const val forgeLoaderRange = "[62,)"

    // NeoForge: 26.1.2.94 is the head of the 26.1 line and the only line with stable builds —
    // every 26.1.0.x and 26.1.1.x build is still -beta. The range covers all three anyway, because
    // :common + :neoforge compile clean against 26.1.0.19-beta and 26.1.1.15-beta as well
    // (checked 2026-08-04), which also proves every accesstransformer entry resolves on those jars.
    const val neoForge = "26.1.2.94"
    const val neoForgeVersionRange = "[26.1.0,)"
    const val neoForgeLoaderRange = "[4,)"

    const val fabric = "0.145.1+26.1"
    const val fabricLoaderMin = "0.19.3"
    const val fabricLoader = "0.19.3"
}
