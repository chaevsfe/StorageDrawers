import com.texelsaurus.Properties
import com.texelsaurus.Versions
import net.darkhax.curseforgegradle.Constants
import net.darkhax.curseforgegradle.TaskPublishCurseForge

plugins {
    id("modloader-conv")
    id("net.neoforged.moddev") version ("2.0.143")
    id("com.modrinth.minotaur")
}

neoForge {
    version = Versions.neoForge
//  accessTransformers.add(file('src/main/resources/META-INF/accesstransformer.cfg'))
    runs {
        register("client") {
            client()
        }
        register("server") {
            server()
            programArgument("--nogui")
        }
    }

    mods {
        register(Properties.modid) {
            sourceSet(sourceSets.main.get())
        }
    }
}

// ModDevGradle's run tasks are plain JavaExec, which defaults to an empty stdin -- unlike Loom's,
// which inherits it. Without this the dedicated server accepts no console commands at all, so
// there is no way to drive a QA session ("tail -f run/cmds.txt | ./gradlew :neoforge:runServer").
tasks.named<JavaExec>("runServer") {
    standardInput = System.`in`
}

dependencies {
    // JEI
    // runtimeOnly("mezz.jei:jei-1.21.9-neoforge:25.0.0.2")
    // JADE — 26.1.8 covers the whole 26.1 patch line (26.1, 26.1.1, 26.1.2), matching the jar we
    // ship. On the 26.2 branch this is 26.2.8+neoforge.
    compileOnly("maven.modrinth:jade:26.1.8+neoforge")
}

tasks.create<TaskPublishCurseForge>("publishCurseForge") {
    dependsOn(tasks.jar)

    disableVersionDetection()
    apiToken = System.getenv("CURSEFORGE_API_KEY") ?: "debug_key"

    val mainFile = upload(Properties.curseProjectId, tasks.jar.get().archiveFile)
    mainFile.displayName = "${Properties.name}-${Versions.minecraft}-neoforge-$version"
    mainFile.changelogType = "markdown"
    mainFile.changelog = (File(rootDir, "CHANGELOG.last.md").takeIf { it.exists() }?.readText() ?: "")
    mainFile.releaseType = Properties.distRelease
    Properties.distGameVersions.split(',').forEach { v -> mainFile.addGameVersion(v) }
    mainFile.addModLoader("NeoForge")
}

modrinth {
    token.set(System.getenv("MODRINTH_API_KEY") ?: "debug_key")
    projectId.set(Properties.modrinthProjectId)
    changelog.set((File(rootDir, "CHANGELOG.last.md").takeIf { it.exists() }?.readText() ?: ""))
    versionName.set("${Properties.name}-${Versions.minecraft}-neoforge-$version")
    // Modrinth version numbers are unique per project, and :fabric already claims the bare
    // "<mc>-<mod>" form, so a loader suffix is required for both files to exist side by side.
    versionNumber.set("${Versions.minecraft}-${Versions.mod}+neoforge")
    versionType.set(Properties.distRelease)
    gameVersions.set(Properties.distGameVersions.split(','))
    uploadFile.set(tasks.jar.get())
    loaders.add("neoforge")
}
tasks.modrinth.get().dependsOn(tasks.jar)
