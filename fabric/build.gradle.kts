import com.texelsaurus.Properties
import com.texelsaurus.Versions
import net.darkhax.curseforgegradle.TaskPublishCurseForge
import net.darkhax.curseforgegradle.Constants as CFG_Constants

plugins {
    id("modloader-conv")
    id("net.fabricmc.fabric-loom") version "1.18.0-alpha.9"
    id("com.modrinth.minotaur")
}

dependencies {
    minecraft("com.mojang:minecraft:${Versions.minecraft}")
    // No mappings(): 26.1 is the first unobfuscated Minecraft release, so there is
    // nothing to remap against and Loom no longer remaps.
    implementation("net.fabricmc:fabric-loader:${Versions.fabricLoader}")
    implementation("net.fabricmc.fabric-api:fabric-api:${Versions.fabric}")

    compileOnly("fuzs.forgeconfigapiport:forgeconfigapiport-fabric:26.1.5") {
        // FCAP 26.1.5 is built against MC 26.1.2 and pulls fabric-api 0.149.1+26.1.2,
        // which Gradle would promote over our 0.145.1+26.1 on the compile classpath --
        // Loom then puts BOTH module sets on the dev launch classpath and the client
        // crashes on cross-version fabric-api internals. We only need FCAP's own classes.
        exclude(group = "net.fabricmc.fabric-api")
    }

    //compileOnlyApi("mezz.jei:jei-${Versions.minecraft}-fabric-api:19.8.2.99")
    //runtimeOnly("mezz.jei:jei-${Versions.minecraft}-fabric:19.8.2.99")
}

loom {
    accessWidenerPath = file("src/main/resources/storagedrawers.fabric.accesswidener")
    // No mixin block: the mod declares "mixins": [] and ships no mixins.json, and
    // refmaps are meaningless against an unobfuscated 26.x.
    runs {
        named("client") {
            client()
            configName = "Fabric Client"
            ideConfigGenerated(true)
            runDir("run")
        }
    }
}

// CHANGELOG.last.md is kept local-only (not committed); guard so a fresh clone still builds.
val lastChangelog = File(rootDir, "CHANGELOG.last.md").takeIf { it.exists() }?.readText() ?: ""

tasks.create<TaskPublishCurseForge>("publishCurseForge") {
    dependsOn(tasks.jar)

    disableVersionDetection()
    apiToken = System.getenv("CURSEFORGE_API_KEY") ?: "debug_key"

    val mainFile = upload(Properties.curseProjectId, tasks.jar.get().archiveFile)
    mainFile.displayName = "${Properties.name}-${Versions.minecraft}-fabric-$version"
    mainFile.changelogType = "markdown"
    mainFile.changelog = lastChangelog
    mainFile.releaseType = Properties.distRelease
    Properties.distGameVersions.split(',').forEach { v -> mainFile.addGameVersion(v) }
    mainFile.addModLoader("Fabric")
    mainFile.addRequirement("fabric-api")
    mainFile.addOptional("forge-config-api-port-fabric")
}

modrinth {
    token.set(System.getenv("MODRINTH_API_KEY") ?: "debug_key")
    projectId.set(Properties.modrinthProjectId)
    changelog.set(lastChangelog)
    versionName.set("${Properties.name}-${Versions.minecraft}-fabric-$version")
    versionNumber.set("${Versions.minecraft}-${Versions.mod}")
    versionType.set(Properties.distRelease)
    gameVersions.set(Properties.distGameVersions.split(','))
    uploadFile.set(tasks.jar.get())
    loaders.add("fabric")

    dependencies {
        required.project("fabric-api")
        optional.project("forge-config-api-port")
    }
}
tasks.modrinth.get().dependsOn(tasks.jar)