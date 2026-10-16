package com.enderbyte.enderlib.config;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.jspecify.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * YAML file plumbing shared by {@link Configs} and {@link Messages}.
 *
 * <p>Paths use an internal separator instead of {@code '.'} so keys may contain dots
 * (for example {@code "1.5"} or {@code "minecraft:stone"}).</p>
 */
final class YamlFiles {

    static final char SEP = '\u001F';

    private YamlFiles() {
    }

    static YamlConfiguration newYaml() {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.options().pathSeparator(SEP).parseComments(true);
        return yaml;
    }

    static YamlConfiguration load(File file) {
        YamlConfiguration yaml = newYaml();
        if (file.isFile()) {
            try {
                yaml.load(file);
            } catch (IOException e) {
                throw new ConfigException("Could not read " + file.getName() + ": " + e.getMessage(), e);
            } catch (InvalidConfigurationException e) {
                throw new ConfigException("YAML syntax error in " + file.getName() + ": " + e.getMessage(), e);
            }
        }
        return yaml;
    }

    static void save(YamlConfiguration yaml, File file) {
        try {
            File parent = file.getParentFile();
            if (parent != null) {
                parent.mkdirs();
            }
            yaml.save(file);
        } catch (IOException e) {
            throw new ConfigException("Could not write " + file.getName() + ": " + e.getMessage(), e);
        }
    }

    /** Converts a section to nested plain maps and lists. */
    static Map<String, Object> toTree(ConfigurationSection section) {
        Map<String, Object> out = new LinkedHashMap<>();
        for (String key : section.getKeys(false)) {
            out.put(key, plain(section.get(key)));
        }
        return out;
    }

    private static @Nullable Object plain(@Nullable Object value) {
        if (value instanceof ConfigurationSection s) {
            return toTree(s);
        }
        if (value instanceof List<?> list) {
            List<Object> out = new ArrayList<>(list.size());
            list.forEach(v -> out.add(plain(v)));
            return out;
        }
        return value;
    }

    static String path(List<String> segments) {
        return String.join(String.valueOf(SEP), segments);
    }

    /**
     * Writes {@code value} under {@code path} leaf by leaf, so existing comments on intermediate
     * sections survive.
     */
    @SuppressWarnings("unchecked")
    static void setDeep(ConfigurationSection yaml, List<String> path, @Nullable Object value) {
        if (value instanceof Map<?, ?> map) {
            if (map.isEmpty()) {
                if (!yaml.isConfigurationSection(path(path))) {
                    yaml.createSection(path(path));
                }
                return;
            }
            for (Map.Entry<?, ?> e : ((Map<Object, Object>) map).entrySet()) {
                List<String> child = new ArrayList<>(path);
                child.add(String.valueOf(e.getKey()));
                setDeep(yaml, child, e.getValue());
            }
            return;
        }
        yaml.set(path(path), value);
    }

    /** Value at {@code path} in a plain tree, or {@code null}. */
    @SuppressWarnings("unchecked")
    static @Nullable Object at(Map<String, Object> tree, List<String> path) {
        Object current = tree;
        for (String segment : path) {
            if (!(current instanceof Map<?, ?> m)) {
                return null;
            }
            current = ((Map<String, Object>) m).get(segment);
        }
        return current;
    }
}
