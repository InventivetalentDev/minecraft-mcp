pluginManagement {
    repositories {
        maven("https://maven.fabricmc.net/") { name = "Fabric" }
        gradlePluginPortal()
    }
}

// Prevent libraries.minecraft.net (added by Loom) from serving non-Mojang artifacts.
// That CDN returns 503 for unknown paths, which blocks Gradle from falling through to Maven Central.
dependencyResolutionManagement {
    @Suppress("UnstableApiUsage")
    repositoriesMode.set(RepositoriesMode.PREFER_PROJECT)
}

rootProject.name = "minecraft-mcp-fabric"
