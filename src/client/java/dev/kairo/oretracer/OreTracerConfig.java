package dev.kairo.oretracer;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumMap;

public final class OreTracerConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path PATH = FabricLoader.getInstance().getConfigDir().resolve("oretracer.json");

    public boolean enabled = true;
    public int scanRadius = 32;
    public EnumMap<OreType, Boolean> ores = new EnumMap<>(OreType.class);

    public OreTracerConfig() {
        for (OreType type : OreType.values()) {
            ores.put(type, true);
        }
    }

    public boolean isEnabled(OreType type) {
        return ores.getOrDefault(type, true);
    }

    public void toggle(OreType type) {
        ores.put(type, !isEnabled(type));
        save();
    }

    public static OreTracerConfig load() {
        if (Files.isRegularFile(PATH)) {
            try (Reader reader = Files.newBufferedReader(PATH)) {
                OreTracerConfig loaded = GSON.fromJson(reader, OreTracerConfig.class);
                if (loaded != null) {
                    if (loaded.ores == null) {
                        loaded.ores = new EnumMap<>(OreType.class);
                    }
                    for (OreType type : OreType.values()) {
                        loaded.ores.putIfAbsent(type, true);
                    }
                    loaded.scanRadius = Math.max(8, Math.min(64, loaded.scanRadius));
                    return loaded;
                }
            } catch (IOException | RuntimeException ignored) {
            }
        }
        OreTracerConfig config = new OreTracerConfig();
        config.save();
        return config;
    }

    public void save() {
        try {
            Files.createDirectories(PATH.getParent());
            try (Writer writer = Files.newBufferedWriter(PATH)) {
                GSON.toJson(this, writer);
            }
        } catch (IOException ignored) {
        }
    }
}
