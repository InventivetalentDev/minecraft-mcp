plugins {
    java
    id("fabric-loom") version "1.16.1"
}

group = property("group")!!
version = property("version")!!

repositories {
    // Ensure Java-WebSocket (and any other non-Minecraft libs) resolve from Maven Central
    // before Loom's injected libraries.minecraft.net repository (which returns 503 for them).
    exclusiveContent {
        forRepository { mavenCentral() }
        filter { includeGroup("org.java-websocket") }
    }
    mavenCentral()
}

loom {
    // MC 26.1.2 has no intermediary or obfuscation; disable the mapping layer.
    @Suppress("UnstableApiUsage")
    noIntermediateMappings()
}

dependencies {
    minecraft("com.mojang:minecraft:${property("minecraft_version")}")
    // MC 26.1.2 is deobfuscated — no mappings() line; modImplementation is unavailable.
    // fabric-loader and fabric-api go on the regular compile+runtime classpath.
    implementation("net.fabricmc:fabric-loader:${property("loader_version")}")
    implementation("net.fabricmc.fabric-api:fabric-api:${property("fabric_version")}")

    // Bundle Java-WebSocket into the mod jar (jar-in-jar)
    include(implementation("org.java-websocket:Java-WebSocket:1.5.7")!!)
}

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(25))
    sourceCompatibility = JavaVersion.VERSION_25
    targetCompatibility = JavaVersion.VERSION_25
    withSourcesJar()
}

val modVersion: String = project.version.toString()
val mcVer: String = project.property("minecraft_version").toString()
val loaderVer: String = project.property("loader_version").toString()
val fabricVer: String = project.property("fabric_version").toString()

tasks {
    compileJava {
        options.encoding = "UTF-8"
        options.release.set(25)
    }
    processResources {
        val props = mapOf(
            "version"           to modVersion,
            "minecraft_version" to mcVer,
            "loader_version"    to loaderVer,
            "fabric_version"    to fabricVer
        )
        inputs.properties(props)
        filesMatching("fabric.mod.json") { expand(props) }
    }
}
