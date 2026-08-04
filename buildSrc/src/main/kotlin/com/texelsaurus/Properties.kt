package com.texelsaurus;

object Properties {
    const val group = "com.texelsaurus.minecraft.storagedrawers"
    const val name = "Storage Drawers"
    const val filename = "StorageDrawers"
    const val author = "Texelsaur"

    // Port maintainer. The GitHub handle goes in `authors`, so it appears alongside the
    // original author in the mod list byline. There is no Contributors block: one person
    // listed under two names, both linking to the same profile, reads as two people.
    const val maintainer = "chaevsfe"

    // This fork. `homepage` is the mod list's Website button, so it points at this fork's
    // download page -- it used to be the original's CurseForge project, which sent anyone
    // clicking it to a different mod.
    const val homepageUrl = "https://modrinth.com/mod/storagedrawers-unofficial-fabric-port"
    const val sourcesUrl = "https://github.com/chaevsfe/StorageDrawers"
    const val issuesUrl = "https://github.com/chaevsfe/StorageDrawers/issues"
    const val modid = "storagedrawers"

    // Both of these were the ORIGINAL project's ids. Publishing with them would attempt to
    // upload this fork to Texelsaur's pages, so they are placeholders until this fork has its
    // own. Replace modrinthProjectId with the id from the fork's Modrinth project settings.
    // Must stay numeric: CurseForgeGradle parses it at configuration time, so a word here
    // breaks every build, not just publishing. 0 is a deliberate no-such-project.
    const val curseProjectId = "0"
    const val modrinthProjectId = "3bqn07Ul"
    const val description = "Interactive compartment storage for your workshops"
    const val license = "MIT"
    const val distRelease = "release"
    // One jar covers the whole 26.1 patch line: fabric.mod.json declares >=26.1 <26.2, and
    // all 22 accesswidener entries plus RenderTypes.solidMovingBlock verify unchanged against
    // the real 26.1.1 and 26.1.2 client jars (checked 2026-08-03).
    const val distGameVersions = "26.1,26.1.1,26.1.2"
}
