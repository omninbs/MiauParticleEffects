package org.miau.particleeffects.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;

public final class ConfigManager {

    private static final Logger LOGGER = LoggerFactory.getLogger("miauparticleeffects");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private static Path path;
    private static MiauParticleEffectsConfig config = new MiauParticleEffectsConfig();

    private ConfigManager() {
    }

    public static void init() {
        path = FabricLoader.getInstance().getConfigDir().resolve("miauparticleeffects.json");
        if (Files.isRegularFile(path)) {
            try (Reader reader = Files.newBufferedReader(path)) {
                MiauParticleEffectsConfig loaded = GSON.fromJson(reader, MiauParticleEffectsConfig.class);
                if (loaded != null) {
                    config = normalize(loaded);
                }
            } catch (IOException | RuntimeException e) {
                LOGGER.warn("读取 MiauParticleEffects.json 失败，回退默认配置", e);
                config = new MiauParticleEffectsConfig();
            }
        } else {
            config = new MiauParticleEffectsConfig();
        }
        save();
    }

    private static MiauParticleEffectsConfig normalize(MiauParticleEffectsConfig c) {
        if (c.density < 0.01 || c.density > 1.0) {
            c.density = 0.1;
        }
        if (c.defaultScale <= 0 || c.defaultScale > 64) {
            c.defaultScale = 1.0F;
        }
        return c;
    }

    public static void save() {
        if (path == null) {
            return;
        }
        try {
            Files.writeString(path, GSON.toJson(config));
        } catch (IOException e) {
            LOGGER.warn("保存 MiauParticleEffects.json 失败", e);
        }
    }

    public static MiauParticleEffectsConfig get() {
        return config;
    }
}