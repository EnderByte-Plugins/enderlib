package com.enderbyte.enderlib.config;

import com.enderbyte.enderlib.core.EnderLib;

import java.io.File;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Loads typed config files from the plugin's data folder.
 *
 * <pre>{@code
 * @Header("Main settings for MyPlugin")
 * public final class Settings {
 *     @Comment("How many homes a player may set")
 *     @Range(min = 1, max = 100)
 *     public int maxHomes = 3;
 *
 *     public Duration teleportDelay = Duration.ofSeconds(3);
 *     public Component joinMessage = Text.mm("<green>Welcome!");
 *     public Map<String, Integer> rankLimits = Map.of("vip", 10);
 * }
 *
 * ConfigHandle<Settings> settings = Configs.of(lib).load("config.yml", Settings.class);
 * }</pre>
 *
 * If the plugin jar contains a file of the same name it is copied out first (keeping its comments);
 * keys it lacks are then filled in from the class defaults.
 */
public final class Configs {

    private final EnderLib lib;
    private final ConfigMapper mapper = ConfigMapper.standard();
    private final Map<String, ConfigHandle<?>> handles = new ConcurrentHashMap<>();

    private Configs(EnderLib lib) {
        this.lib = lib;
    }

    /** The config service of {@code lib}. */
    public static Configs of(EnderLib lib) {
        return lib.service(Configs.class, Configs::new);
    }

    /** The mapper used for every file; register custom {@link TypeSerializer}s here before loading. */
    public ConfigMapper mapper() {
        return mapper;
    }

    /**
     * Loads (creating or completing if needed) {@code path} relative to the data folder.
     *
     * @throws ConfigException if the file is invalid
     */
    public <T> ConfigHandle<T> load(String path, Class<T> type) {
        File file = new File(lib.plugin().getDataFolder(), path);
        if (!file.exists() && lib.plugin().getResource(path.replace('\\', '/')) != null) {
            lib.plugin().saveResource(path.replace('\\', '/'), false);
        }
        ConfigHandle<T> handle = new ConfigHandle<>(file, type, mapper);
        handles.put(path, handle);
        return handle;
    }

    /** Reloads every file loaded through this service. Stops at, and rethrows, the first failure. */
    public void reloadAll() {
        handles.values().forEach(ConfigHandle::reload);
    }
}
