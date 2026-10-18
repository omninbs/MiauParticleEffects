package org.miau.particleeffects.exparticle;

import com.google.gson.Gson;
import net.fabricmc.loader.api.FabricLoader;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * ExParticle 部分的配置（移植自 ExParticle 的 ExParticleConfig），读写 {@code config/exparticle.json}。
 */
public final class ExParticleConfig {

    public static ConfigData config = new ConfigData();

    private ExParticleConfig() {
    }

    public static void init() {
        try {
            Gson gson = new Gson();
            Path dir = FabricLoader.getInstance().getConfigDir();
            Files.createDirectories(dir);
            Path file = dir.resolve("exparticle.json");
            if (!Files.exists(file)) {
                try (BufferedWriter writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
                    gson.toJson(config, writer);
                }
            }
            try (BufferedReader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
                ConfigData loaded = gson.fromJson(reader, ConfigData.class);
                if (loaded != null) {
                    config = loaded;
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /** 配置数据：与原 ExParticle 保持一致的字段名。 */
    public static class ConfigData {
        public int maxParticleCount = 65536;
        public int maxParticleTickMillis = 1000;

        public ConfigData() {
        }
    }
}
