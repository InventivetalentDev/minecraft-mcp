plugins {
    java
    id("com.gradleup.shadow") version "8.3.6"
}

group = property("group")!!
version = property("version")!!

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:${property("mcVersion")}-R0.1-SNAPSHOT")
    // Shade the WebSocket library; relocated to avoid classpath conflicts.
    implementation("org.java-websocket:Java-WebSocket:1.5.7")
}

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(21))
}

val pluginVersion: String = project.version.toString()
val mcVersionString: String = project.property("mcVersion").toString()

tasks {
    compileJava {
        options.encoding = "UTF-8"
    }
    shadowJar {
        archiveClassifier.set("")
        // Note: Paper plugins each get their own PluginClassLoader, so
        // bundling org.java_websocket without relocation is safe — each
        // plugin loads its own copy. Relocation in shadow 8.3.5 hits a
        // bug with Java 21 invokedynamic string concatenation.
    }
    build {
        dependsOn(shadowJar)
    }
    processResources {
        val props = mapOf(
            "version" to pluginVersion,
            "mcVersion" to mcVersionString
        )
        inputs.properties(props)
        filesMatching("plugin.yml") { expand(props) }
    }
}
