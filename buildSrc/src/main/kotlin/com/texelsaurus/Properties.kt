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

    // This fork's Modrinth project. Declared before homepageUrl because that URL is built from
    // it -- a const val cannot reference one declared further down the object.
    const val modrinthProjectId = "3bqn07Ul"

    // This fork. `homepage` is the mod list's Website button, so it points at this fork's
    // download page -- it used to be the original's CurseForge project, which sent anyone
    // clicking it to a different mod. Use the project-id permalink, not the /mod/<slug> form:
    // the slug follows the project title, so renaming the project -- which shipping a second
    // loader may well mean -- would dead-link every jar already published.
    const val homepageUrl = "https://modrinth.com/project/$modrinthProjectId"
    const val sourcesUrl = "https://github.com/chaevsfe/StorageDrawers"
    const val issuesUrl = "https://github.com/chaevsfe/StorageDrawers/issues"
    const val modid = "storagedrawers"

    // Still the ORIGINAL project's id. Publishing with it would attempt to upload this fork to
    // Texelsaur's CurseForge page, so it is a placeholder until this fork has its own. Must stay
    // numeric: CurseForgeGradle parses it at configuration time, so a word here breaks every
    // build, not just publishing. 0 is a deliberate no-such-project.
    const val curseProjectId = "0"
    const val description = "Interactive compartment storage for your workshops"
    const val license = "MIT"
    const val distRelease = "release"
    // One jar covers the whole 26.1 patch line: fabric.mod.json declares >=26.1 <26.2, and
    // all 22 accesswidener entries plus RenderTypes.solidMovingBlock verify unchanged against
    // the real 26.1.1 and 26.1.2 client jars (checked 2026-08-03).
    const val distGameVersions = "26.1,26.1.1,26.1.2"
}
