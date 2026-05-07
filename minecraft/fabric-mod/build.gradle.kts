plugins {
    java
    id("fabric-loom") version "1.9.+"
}

group = property("group")!!
version = property("version")!!

repositories {
    mavenCentral()
}

dependencies {
    minecraft("com.mojang:minecraft:${property("minecraft_version")}")
    mappings("net.fabricmc:yarn:${property("yarn_mappings")}:v2")
    modImplementation("net.fabricmc:fabric-loader:${property("loader_version")}")
    modImplementation("net.fabricmc.fabric-api:fabric-api:${property("fabric_version")}")

    // Bundle Java-WebSocket into the mod jar (jar-in-jar)
    include(implementation("org.java-websocket:Java-WebSocket:1.5.7")!!)
}

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(21))
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
    withSourcesJar()
}

tasks {
    compileJava {
        options.encoding = "UTF-8"
        options.release.set(21)
    }
    processResources {
        val props = mapOf(
            "version"           to project.version,
            "minecraft_version" to property("minecraft_version"),
            "loader_version"    to property("loader_version"),
            "fabric_version"    to property("fabric_version")
        )
        inputs.properties(props)
        filesMatching("fabric.mod.json") { expand(props) }
    }
}
