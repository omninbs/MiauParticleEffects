plugins {
    // Applies the correct Loom variant based on the Minecraft version being built
    // (obfuscated fabric-loom-remap < 26.1, unobfuscated fabric-loom >= 26.1).
    id("dev.kikugie.loom-back-compat")
}

// DO NOT set group = ... (managed by MavenPublication defaults)
version = "${property("mod.version")}+${sc.current.version}"
base {
    archivesName = property("mod.id") as String
}

val requiredJava: JavaVersion = when {
    sc.current.parsed >= "26.1" -> JavaVersion.VERSION_25
    sc.current.parsed >= "1.20.5" -> JavaVersion.VERSION_21
    sc.current.parsed >= "1.18" -> JavaVersion.VERSION_17
    else -> JavaVersion.VERSION_1_8
}

val requiredJavaRelease: Int = when {
    sc.current.parsed >= "26.1" -> 25
    sc.current.parsed >= "1.20.5" -> 21
    sc.current.parsed >= "1.18" -> 17
    else -> 8
}

dependencies {
    fun fapi(module: String) {
        modImplementation(fabricApi.module(module, sc.properties["deps.fabric_api"]))
    }

    minecraft("com.mojang:minecraft:${sc.current.version}")
    // Applies Mojang Mappings on obfuscated versions; no-op on unobfuscated (26.1+).
    loomx.applyMojangMappings()

    // `modImplementation` works on both loom variants (converted to `implementation` on 26.1+).
    modImplementation("net.fabricmc:fabric-loader:${property("deps.fabric_loader")}")

    // Fabric API modules actually used by this mod
    fapi("fabric-command-api-v2")
    fapi("fabric-events-interaction-v0")
    fapi("fabric-lifecycle-events-v1")
    fapi("fabric-networking-api-v1")
    fapi("fabric-particles-v1")
    fapi("fabric-data-generation-api-v1")
}

loom {
    splitEnvironmentSourceSets()

    mods {
        register("miauparticleeffects") {
            sourceSet(sourceSets["main"])
            sourceSet(sourceSets["client"])
        }
    }
}

java {
    withSourcesJar()
    targetCompatibility = requiredJava
    sourceCompatibility = requiredJava
}

fun ProcessResources.configureResources() {
    fun MutableMap<String, String>.register(key: String, value: String) {
        inputs.property(key, value)
        put(key, value)
    }

    val props = buildMap {
        register("version", sc.properties["mod.version"])
        register("minecraft", sc.properties["mod.mc_compat"])
        val loaderPrefix = Regex("\\d+\\.\\d+").find(sc.properties.get<String>("deps.fabric_loader"))!!.value
        register("loader", loaderPrefix)
    }

    filesMatching("fabric.mod.json") {
        expand(props)
    }

    val mixinJava = "JAVA_$requiredJavaRelease"
    filesMatching("*.mixins.json") {
        expand("java" to mixinJava)
    }
}

// Applies the same resource expansion to both the main and client resource tasks
// (the client source set src/client/resources is processed by processClientResources).
tasks.withType<ProcessResources>().configureEach {
    configureResources()
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.release.set(requiredJavaRelease)
}