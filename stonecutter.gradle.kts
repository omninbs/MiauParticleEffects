plugins {
    id("dev.kikugie.stonecutter")
}

stonecutter active "26.2"

// See https://stonecutter.kikugie.dev/wiki/config/params
stonecutter parameters {
    swaps["mod_version"] = "\"${property("mod.version")}\";"
    swaps["minecraft"] = "\"${node.metadata.version}\";"
    constants["release"] = property("mod.id") != "template"
    dependencies["fapi"] = node.project.property("deps.fabric_api") as String

    replacements {
        // Minecraft renamed `ResourceLocation` to `Identifier` in 1.21.11.
        // Write the old name in source so pre-1.21.11 versions compile as-is.
        string(current.parsed >= "1.21.11") {
            replace("ResourceLocation", "Identifier")
        }

        // Fabric API renamed the payload registry play-stage accessors in 26.1.
        string(current.parsed >= "26.1") {
            replace("playS2C", "clientboundPlay")
            replace("playC2S", "serverboundPlay")
            replace("ParticleFactoryRegistry", "ParticleProviderRegistry")
        }
    }
}