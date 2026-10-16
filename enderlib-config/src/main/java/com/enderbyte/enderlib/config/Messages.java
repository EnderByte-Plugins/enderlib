package com.enderbyte.enderlib.config;

import com.enderbyte.enderlib.core.EnderLib;
import com.enderbyte.enderlib.text.Text;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.kyori.adventure.title.Title;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.jspecify.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

/**
 * Locale-aware MiniMessage message bundles.
 *
 * <p>Files live in {@code <data folder>/lang/<locale>.yml} ({@code en_us.yml}, {@code de_de.yml}, ...).
 * Bundled files under {@code lang/} in the plugin jar are copied out on first start. A player gets
 * their client language when a bundle exists for it, then the bare language ({@code de}), then the
 * default locale.</p>
 *
 * <pre>{@code
 * # lang/en_us.yml
 * prefix: "<dark_gray>[<gold>Shop</gold>]</dark_gray> "
 * shop:
 *   bought: "<prefix><gray>You bought <white><amount>x <item></white> for <gold>$<price>"
 *   full: "[actionbar]<red>Your inventory is full"
 *   welcome: "[title]<gold>Welcome|<gray>to the shop"
 *   help:
 *     - "<gold>/shop buy <item>"
 *     - "<gold>/shop sell <item>"
 * }</pre>
 *
 * <pre>{@code
 * Messages messages = Messages.load(lib);
 * messages.send(player, "shop.bought", Placeholders.of().unparsed("amount", 3).unparsed("item", "Apple").number("price", 12.5).build());
 * }</pre>
 *
 * <p>Value prefixes: {@code [actionbar]} sends an action bar, {@code [title]} shows {@code title|subtitle},
 * {@code [none]} (or an empty string) disables the message. Lists send one chat line each.</p>
 */
public final class Messages {

    private static final String FOLDER = "lang";

    private final EnderLib lib;
    private final String defaultLocale;
    private final Map<String, Map<String, Object>> bundles = new ConcurrentHashMap<>();
    private final Map<String, Object> programmaticDefaults = new LinkedHashMap<>();
    private final Set<String> warnedMissing = ConcurrentHashMap.newKeySet();

    private Messages(EnderLib lib, String defaultLocale) {
        this.lib = lib;
        this.defaultLocale = normalise(defaultLocale);
        reload();
    }

    /** Loads the bundles with {@code en_us} as the default locale. */
    public static Messages load(EnderLib lib) {
        return load(lib, "en_us");
    }

    /** Loads the bundles; one {@code Messages} per {@link EnderLib}. */
    public static Messages load(EnderLib lib, String defaultLocale) {
        return lib.service(Messages.class, l -> new Messages(l, defaultLocale));
    }

    /**
     * Adds keys (dotted path to string or list of strings) that are written into the default-locale
     * file when missing. Library modules use this to ship their own messages.
     */
    public synchronized Messages addDefaults(Map<String, ?> defaults) {
        programmaticDefaults.putAll(defaults);
        reload();
        return this;
    }

    /** Re-reads every bundle from disk. */
    public synchronized void reload() {
        File folder = new File(lib.plugin().getDataFolder(), FOLDER);
        copyBundledFiles(folder);
        bundles.clear();
        File defaultFile = new File(folder, defaultLocale + ".yml");
        if (!programmaticDefaults.isEmpty()) {
            YamlConfiguration yaml = YamlFiles.load(defaultFile);
            boolean changed = false;
            for (Map.Entry<String, Object> e : programmaticDefaults.entrySet()) {
                String path = e.getKey().replace('.', YamlFiles.SEP);
                if (!yaml.contains(path)) {
                    yaml.set(path, e.getValue());
                    changed = true;
                }
            }
            if (changed) {
                YamlFiles.save(yaml, defaultFile);
            }
        }
        File[] files = folder.listFiles((dir, name) -> name.endsWith(".yml"));
        if (files != null) {
            for (File f : files) {
                String locale = normalise(f.getName().substring(0, f.getName().length() - 4));
                Map<String, Object> flat = new LinkedHashMap<>();
                flatten("", YamlFiles.toTree(YamlFiles.load(f)), flat);
                bundles.put(locale, flat);
            }
        }
        bundles.computeIfAbsent(defaultLocale, k -> new LinkedHashMap<>());
        warnedMissing.clear();
        Text.clearCache();
    }

    // ------------------------------------------------------------------ lookup

    public boolean has(String key) {
        return bundle(defaultLocale).containsKey(key);
    }

    /** The raw string for {@code key} in {@code locale}, or {@code null}. Lists are joined with newlines. */
    public @Nullable String raw(Locale locale, String key) {
        Object v = lookup(locale, key);
        if (v instanceof List<?> list) {
            List<String> lines = new ArrayList<>();
            list.forEach(o -> lines.add(String.valueOf(o)));
            return String.join("\n", lines);
        }
        return v == null ? null : v.toString();
    }

    /** One component for {@code key}; list values are joined with newlines. */
    public Component get(Locale locale, String key, TagResolver... resolvers) {
        Object v = lookup(locale, key);
        if (v == null) {
            return missing(key);
        }
        if (v instanceof List<?> list) {
            return Component.join(net.kyori.adventure.text.JoinConfiguration.newlines(), lines(locale, key, resolvers));
        }
        return parse(locale, stripPrefix(v.toString()), resolvers);
    }

    public Component get(Audience audience, String key, TagResolver... resolvers) {
        return get(localeOf(audience), key, resolvers);
    }

    /** Default-locale component, for console output and logs. */
    public Component get(String key, TagResolver... resolvers) {
        return get(Locale.forLanguageTag(defaultLocale.replace('_', '-')), key, resolvers);
    }

    /** Every line of a list value (or the single value as one line). */
    public List<Component> lines(Locale locale, String key, TagResolver... resolvers) {
        Object v = lookup(locale, key);
        if (v == null) {
            return List.of(missing(key));
        }
        List<Component> out = new ArrayList<>();
        if (v instanceof List<?> list) {
            for (Object line : list) {
                out.add(parse(locale, String.valueOf(line), resolvers));
            }
        } else {
            out.add(parse(locale, v.toString(), resolvers));
        }
        return out;
    }

    public List<Component> lines(Audience audience, String key, TagResolver... resolvers) {
        return lines(localeOf(audience), key, resolvers);
    }

    /** Sends {@code key} honouring {@code [actionbar]}, {@code [title]} and {@code [none]}. */
    public void send(Audience audience, String key, TagResolver... resolvers) {
        Locale locale = localeOf(audience);
        Object v = lookup(locale, key);
        if (v == null) {
            audience.sendMessage(missing(key));
            return;
        }
        if (v instanceof List<?>) {
            lines(locale, key, resolvers).forEach(audience::sendMessage);
            return;
        }
        String s = v.toString();
        if (s.isEmpty() || s.startsWith("[none]")) {
            return;
        }
        if (s.startsWith("[actionbar]")) {
            audience.sendActionBar(parse(locale, s.substring("[actionbar]".length()), resolvers));
        } else if (s.startsWith("[title]")) {
            String body = s.substring("[title]".length());
            int bar = body.indexOf('|');
            Component title = parse(locale, bar < 0 ? body : body.substring(0, bar), resolvers);
            Component subtitle = bar < 0 ? Component.empty() : parse(locale, body.substring(bar + 1), resolvers);
            audience.showTitle(Title.title(title, subtitle));
        } else {
            audience.sendMessage(parse(locale, s, resolvers));
        }
    }

    /** Sends to several audiences, each in their own language. */
    public void broadcast(Iterable<? extends Audience> audiences, String key, TagResolver... resolvers) {
        for (Audience a : audiences) {
            send(a, key, resolvers);
        }
    }

    public Set<String> locales() {
        return Set.copyOf(bundles.keySet());
    }

    // ------------------------------------------------------------------ internals

    private Component parse(Locale locale, String template, TagResolver... resolvers) {
        Object prefix = lookup(locale, "prefix");
        TagResolver all = TagResolver.resolver(
                TagResolver.resolver(resolvers),
                Placeholder.parsed("prefix", prefix == null ? "" : prefix.toString()));
        return Text.mm(template, all);
    }

    private static String stripPrefix(String s) {
        if (s.startsWith("[actionbar]")) return s.substring("[actionbar]".length());
        if (s.startsWith("[title]")) return s.substring("[title]".length()).replace('|', ' ');
        if (s.startsWith("[none]")) return s.substring("[none]".length());
        return s;
    }

    private @Nullable Object lookup(Locale locale, String key) {
        String tag = normalise(locale.toString());
        Object v = bundle(tag).get(key);
        if (v == null) {
            int underscore = tag.indexOf('_');
            if (underscore > 0) {
                v = bundle(tag.substring(0, underscore)).get(key);
            }
        }
        if (v == null) {
            v = bundle(defaultLocale).get(key);
        }
        if (v == null) {
            v = programmaticDefaults.get(key);
        }
        return v;
    }

    private Map<String, Object> bundle(String locale) {
        return bundles.getOrDefault(locale, Map.of());
    }

    private Locale localeOf(Audience audience) {
        if (audience instanceof Player player) {
            return player.locale();
        }
        return Locale.forLanguageTag(defaultLocale.replace('_', '-'));
    }

    private Component missing(String key) {
        if (warnedMissing.add(key)) {
            lib.logger().warn("Missing message key '{}' in {}", key, FOLDER + "/" + defaultLocale + ".yml");
        }
        return Component.text("<missing:" + key + ">");
    }

    @SuppressWarnings("unchecked")
    private static void flatten(String prefix, Map<String, Object> tree, Map<String, Object> out) {
        for (Map.Entry<String, Object> e : tree.entrySet()) {
            String key = prefix.isEmpty() ? e.getKey() : prefix + "." + e.getKey();
            if (e.getValue() instanceof Map<?, ?> m) {
                flatten(key, (Map<String, Object>) m, out);
            } else if (e.getValue() != null) {
                out.put(key, e.getValue());
            }
        }
    }

    private static String normalise(String locale) {
        return Objects.requireNonNull(locale, "locale").toLowerCase(Locale.ROOT).replace('-', '_');
    }

    /** Copies {@code lang/*.yml} from the plugin jar for every file the server owner does not have yet. */
    private void copyBundledFiles(File folder) {
        File jar;
        try {
            jar = new File(lib.plugin().getClass().getProtectionDomain().getCodeSource().getLocation().toURI());
        } catch (URISyntaxException | SecurityException | NullPointerException e) {
            return;
        }
        if (!jar.isFile()) {
            return;
        }
        try (JarFile jf = new JarFile(jar)) {
            Enumeration<JarEntry> entries = jf.entries();
            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();
                String name = entry.getName();
                if (!entry.isDirectory() && name.startsWith(FOLDER + "/") && name.endsWith(".yml")
                        && name.indexOf('/', FOLDER.length() + 1) < 0
                        && !new File(folder, name.substring(FOLDER.length() + 1)).exists()) {
                    lib.plugin().saveResource(name, false);
                }
            }
        } catch (IOException e) {
            lib.logger().warn("Could not scan plugin jar for bundled language files", e);
        }
    }
}
