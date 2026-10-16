package com.enderbyte.enderlib.config;

import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * A config file bound to a Java type.
 *
 * <pre>{@code
 * ConfigHandle<Settings> settings = Configs.of(lib).load("config.yml", Settings.class);
 * int max = settings.get().maxHomes;
 * settings.onReload(s -> lib.logger().info("Reloaded, max homes = {}", s.maxHomes));
 * }</pre>
 *
 * <p>{@link #get()} always returns a complete, validated object. A failed {@link #reload()} throws and
 * keeps the previous value, so a typo in the file never takes a running server down.</p>
 */
public final class ConfigHandle<T> {

    private final File file;
    private final Class<T> type;
    private final ConfigMapper mapper;
    private final List<Consumer<? super T>> listeners = new CopyOnWriteArrayList<>();
    private volatile T value;

    /**
     * Opens a config file outside of {@link Configs} (for example in tests or a standalone tool).
     *
     * @throws ConfigException if the file is invalid
     */
    public static <T> ConfigHandle<T> open(File file, Class<T> type, ConfigMapper mapper) {
        return new ConfigHandle<>(file, type, mapper);
    }

    ConfigHandle(File file, Class<T> type, ConfigMapper mapper) {
        this.file = file;
        this.type = type;
        this.mapper = mapper;
        this.value = read();
    }

    public T get() {
        return value;
    }

    public File file() {
        return file;
    }

    public Class<T> type() {
        return type;
    }

    /**
     * Re-reads the file, adds any keys that are missing, and notifies {@link #onReload} listeners.
     *
     * @throws ConfigException if the file is invalid; the previous value stays active
     */
    public T reload() {
        T fresh = read();
        this.value = fresh;
        listeners.forEach(l -> l.accept(fresh));
        return fresh;
    }

    /** Replaces the value in memory and writes it to disk, keeping the file's comments. */
    public void set(T newValue) {
        this.value = Objects.requireNonNull(newValue, "newValue");
        save();
    }

    /** Writes the current value to disk, keeping existing comments. */
    @SuppressWarnings("unchecked")
    public void save() {
        YamlConfiguration yaml = YamlFiles.load(file);
        Map<String, Object> tree = (Map<String, Object>) mapper.write(value, type);
        YamlFiles.setDeep(yaml, List.of(), tree);
        YamlFiles.save(yaml, file);
    }

    /** Runs {@code listener} after every successful reload. */
    public ConfigHandle<T> onReload(Consumer<? super T> listener) {
        listeners.add(Objects.requireNonNull(listener, "listener"));
        return this;
    }

    @SuppressWarnings("unchecked")
    private T read() {
        boolean existed = file.isFile();
        YamlConfiguration yaml = YamlFiles.load(file);
        ConfigMapper.Binding<T> binding = mapper.bind(YamlFiles.toTree(yaml), type);
        if (!binding.ok()) {
            throw new ConfigException(file.getName(), binding.problems());
        }
        T bound = Objects.requireNonNull(binding.value());
        if (!existed || !binding.missing().isEmpty()) {
            Map<String, Object> defaults = (Map<String, Object>) mapper.write(bound, type);
            Map<List<String>, List<String>> comments = mapper.comments(type);
            for (List<String> path : binding.missing()) {
                YamlFiles.setDeep(yaml, path, YamlFiles.at(defaults, path));
            }
            comments.forEach((path, lines) -> {
                String p = YamlFiles.path(path);
                if (yaml.contains(p) && yaml.getComments(p).isEmpty()) {
                    yaml.setComments(p, lines);
                }
            });
            Header header = type.getAnnotation(Header.class);
            if (!existed && header != null) {
                yaml.options().setHeader(List.of(header.value()));
            }
            YamlFiles.save(yaml, file);
        }
        return bound;
    }
}
