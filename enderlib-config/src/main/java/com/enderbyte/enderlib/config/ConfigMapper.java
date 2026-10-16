package com.enderbyte.enderlib.config;

import com.enderbyte.enderlib.text.Text;
import com.enderbyte.enderlib.util.Durations;
import io.papermc.paper.registry.RegistryAccess;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import org.bukkit.Keyed;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.jspecify.annotations.Nullable;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.GenericArrayType;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.RecordComponent;
import java.lang.reflect.Type;
import java.lang.reflect.WildcardType;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

/**
 * Binds the plain YAML tree (maps, lists, scalars) to Java objects and back.
 *
 * <p>Server-independent: it never touches files or Bukkit, so it is unit-testable and reusable for
 * JSON or database-stored settings.</p>
 *
 * <h2>Supported types</h2>
 * Strings, primitives and their boxes, {@link BigDecimal}, {@link BigInteger}, enums (case and
 * dash-insensitive), {@link UUID}, {@link Duration} ({@code "1h30m"}), {@link Component} (MiniMessage),
 * {@link NamespacedKey}, {@link Key}, {@link Locale}, {@link Pattern}, registry types such as
 * {@code Sound} or {@code Enchantment} (by key), {@link Optional}, {@link List}, {@link Set},
 * {@link Map}, arrays, and any nested class or record. Anything already of the target type (for
 * example an {@code ItemStack} Bukkit deserialised itself) passes through.
 *
 * <h2>Classes and records</h2>
 * A class needs a no-argument constructor; field initialisers are the defaults. A record's missing
 * components come from its static {@code defaults()} method or {@code DEFAULT} constant, if any.
 * Field names map to kebab-case keys ({@code maxPlayers -> max-players}) unless annotated with
 * {@link Key}.
 */
public final class ConfigMapper {

    private final Map<Class<?>, TypeSerializer<?>> exact = new ConcurrentHashMap<>();
    private final List<Map.Entry<Class<?>, TypeSerializer<?>>> hierarchy = new ArrayList<>();

    public static ConfigMapper standard() {
        return new ConfigMapper();
    }

    private ConfigMapper() {
        register(String.class, TypeSerializer.of(Object::toString, v -> v));
        register(UUID.class, TypeSerializer.of(raw -> UUID.fromString(raw.toString().trim()), UUID::toString));
        register(Duration.class, TypeSerializer.of(
                raw -> raw instanceof Number n ? Duration.ofSeconds(n.longValue()) : Durations.parse(raw.toString()),
                Durations::toCompact));
        registerHierarchy(Component.class, TypeSerializer.of(raw -> Text.mm(raw.toString()), Text::serialize));
        register(NamespacedKey.class, TypeSerializer.of(raw -> {
            NamespacedKey key = NamespacedKey.fromString(raw.toString().trim().toLowerCase(Locale.ROOT));
            if (key == null) {
                throw new IllegalArgumentException("'" + raw + "' is not a valid namespaced key");
            }
            return key;
        }, NamespacedKey::asString));
        register(Key.class, TypeSerializer.of(raw -> Key.key(raw.toString().trim().toLowerCase(Locale.ROOT)), Key::asString));
        register(Locale.class, TypeSerializer.of(raw -> Locale.forLanguageTag(raw.toString().replace('_', '-')), Locale::toLanguageTag));
        register(Pattern.class, TypeSerializer.of(raw -> Pattern.compile(raw.toString()), Pattern::pattern));
    }

    /** Registers a serializer for exactly {@code type}. Later registrations win. */
    public <T> ConfigMapper register(Class<T> type, TypeSerializer<T> serializer) {
        exact.put(type, serializer);
        return this;
    }

    /** Registers a serializer for {@code type} and all of its subtypes. */
    public synchronized <T> ConfigMapper registerHierarchy(Class<T> type, TypeSerializer<? extends T> serializer) {
        hierarchy.add(0, Map.entry(type, serializer));
        return this;
    }

    // ================================================================== binding

    /** Result of {@link #bind}: the object, the keys that were missing, and any problems. */
    public record Binding<T>(@Nullable T value, List<List<String>> missing, List<String> problems) {
        public boolean ok() {
            return problems.isEmpty();
        }
    }

    /**
     * Binds {@code tree} to a new instance of {@code type}, keeping defaults for absent keys.
     * Never throws for bad input: problems are collected in the result.
     */
    public <T> Binding<T> bind(Map<String, ?> tree, Class<T> type) {
        Ctx ctx = new Ctx();
        Object value = readObject(tree, type, new ArrayList<>(), ctx);
        return new Binding<>(type.cast(value), ctx.missing, ctx.problems);
    }

    /** Converts one YAML value to {@code type}. */
    public Object read(Object raw, Type type) {
        Ctx ctx = new Ctx();
        Object v = read(raw, type, new ArrayList<>(), ctx);
        if (!ctx.problems.isEmpty()) {
            throw new IllegalArgumentException(String.join("; ", ctx.problems));
        }
        return v;
    }

    /** Converts a Java value into the plain YAML tree. */
    @SuppressWarnings({"unchecked", "rawtypes"})
    public @Nullable Object write(@Nullable Object value, Type declared) {
        if (value == null) {
            return null;
        }
        Type type = declared;
        Class<?> raw = rawClass(type);
        TypeSerializer serializer = serializerFor(raw);
        if (serializer == null && (raw == Object.class || raw.isInterface()
                && !Collection.class.isAssignableFrom(raw) && !Map.class.isAssignableFrom(raw))) {
            raw = value.getClass();
            type = raw;
            serializer = serializerFor(raw);
        }
        if (serializer != null) {
            return serializer.serialize(value, type, this);
        }
        if (value instanceof Optional<?> opt) {
            Type element = typeArg(type, 0);
            return opt.map(v -> write(v, element)).orElse(null);
        }
        if (isScalar(value)) {
            return value;
        }
        if (value instanceof Enum<?> e) {
            return e.name();
        }
        if (value instanceof Keyed keyed && !(value instanceof Enum<?>)) {
            return keyed.getKey().asString();
        }
        if (value instanceof Collection<?> c) {
            Type element = typeArg(type, 0);
            List<Object> out = new ArrayList<>(c.size());
            c.forEach(v -> out.add(write(v, element)));
            return out;
        }
        if (raw.isArray()) {
            Type element = raw.getComponentType();
            int n = java.lang.reflect.Array.getLength(value);
            List<Object> out = new ArrayList<>(n);
            for (int i = 0; i < n; i++) {
                out.add(write(java.lang.reflect.Array.get(value, i), element));
            }
            return out;
        }
        if (value instanceof Map<?, ?> m) {
            Type keyType = typeArg(type, 0);
            Type valueType = typeArg(type, 1);
            Map<String, Object> out = new LinkedHashMap<>();
            m.forEach((k, v) -> out.put(String.valueOf(write(k, keyType)), write(v, valueType)));
            return out;
        }
        if (isBindable(raw)) {
            Map<String, Object> out = new LinkedHashMap<>();
            for (Property p : properties(raw)) {
                out.put(p.key, write(p.get(value), p.type));
            }
            return out;
        }
        return value; // ConfigurationSerializable and friends: let the YAML layer handle it
    }

    /**
     * Comments declared with {@link Comment} on {@code type} and its nested types, keyed by path segments.
     */
    public Map<List<String>, List<String>> comments(Class<?> type) {
        Map<List<String>, List<String>> out = new LinkedHashMap<>();
        collectComments(type, new ArrayList<>(), out, new java.util.HashSet<>());
        return out;
    }

    // ================================================================== reading

    private static final class Ctx {
        final List<List<String>> missing = new ArrayList<>();
        final List<String> problems = new ArrayList<>();
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private @Nullable Object read(@Nullable Object raw, Type type, List<String> path, Ctx ctx) {
        Class<?> target = boxed(rawClass(type));
        if (raw == null) {
            return target == Optional.class ? Optional.empty() : null;
        }
        try {
            TypeSerializer serializer = serializerFor(target);
            if (serializer != null) {
                return serializer.deserialize(raw, type, this);
            }
            if (target == Optional.class) {
                return Optional.ofNullable(read(raw, typeArg(type, 0), path, ctx));
            }
            if (target.isInstance(raw) && !(raw instanceof Map) && !(raw instanceof Collection)) {
                return raw;
            }
            if (target == Boolean.class) {
                return readBoolean(raw);
            }
            if (Number.class.isAssignableFrom(target)) {
                return readNumber(raw, target);
            }
            if (target == Character.class) {
                String s = raw.toString();
                if (s.length() != 1) {
                    throw new IllegalArgumentException("expected a single character, got '" + s + "'");
                }
                return s.charAt(0);
            }
            if (target.isEnum()) {
                return readEnum(raw, (Class<? extends Enum>) target);
            }
            if (Keyed.class.isAssignableFrom(target)) {
                return readKeyed(raw, (Class<? extends Keyed>) target);
            }
            if (Collection.class.isAssignableFrom(target)) {
                return readCollection(raw, type, target, path, ctx);
            }
            if (target.isArray()) {
                List<?> list = (List<?>) readCollection(raw, List.class, List.class, path, ctx);
                Class<?> component = target.getComponentType();
                Object array = java.lang.reflect.Array.newInstance(component, list.size());
                for (int i = 0; i < list.size(); i++) {
                    java.lang.reflect.Array.set(array, i, read(list.get(i), component, child(path, String.valueOf(i)), ctx));
                }
                return array;
            }
            if (Map.class.isAssignableFrom(target)) {
                return readMap(raw, type, target, path, ctx);
            }
            if (isBindable(target)) {
                if (!(raw instanceof Map<?, ?> m)) {
                    throw new IllegalArgumentException("expected a section, got '" + raw + "'");
                }
                return readObject((Map<String, ?>) m, target, path, ctx);
            }
            throw new IllegalArgumentException("no serializer for " + type.getTypeName());
        } catch (IllegalArgumentException | ClassCastException e) {
            ctx.problems.add(display(path) + ": " + e.getMessage());
            return null;
        }
    }

    private Object readObject(Map<String, ?> tree, Class<?> type, List<String> path, Ctx ctx) {
        if (type.isRecord()) {
            return readRecord(tree, type, path, ctx);
        }
        Object instance = instantiate(type);
        for (Property p : properties(type)) {
            List<String> childPath = child(path, p.key);
            if (!tree.containsKey(p.key)) {
                ctx.missing.add(childPath);
                validate(p, p.get(instance), childPath, ctx);
                continue;
            }
            Object value = read(tree.get(p.key), p.type, childPath, ctx);
            if (value == null && p.rawType.isPrimitive()) {
                continue; // keep default; the problem is already recorded
            }
            if (value != null || tree.get(p.key) == null) {
                p.set(instance, value);
            }
            validate(p, p.get(instance), childPath, ctx);
        }
        return instance;
    }

    private Object readRecord(Map<String, ?> tree, Class<?> type, List<String> path, Ctx ctx) {
        RecordComponent[] components = type.getRecordComponents();
        Object defaults = recordDefaults(type);
        Object[] args = new Object[components.length];
        Class<?>[] types = new Class<?>[components.length];
        List<Property> props = properties(type);
        for (int i = 0; i < components.length; i++) {
            Property p = props.get(i);
            types[i] = components[i].getType();
            List<String> childPath = child(path, p.key);
            Object value;
            if (tree.containsKey(p.key)) {
                value = read(tree.get(p.key), p.type, childPath, ctx);
            } else if (defaults != null) {
                ctx.missing.add(childPath);
                value = p.get(defaults);
            } else {
                ctx.problems.add(display(childPath) + ": required");
                value = null;
            }
            if (value == null && types[i].isPrimitive()) {
                value = defaults != null ? p.get(defaults) : primitiveZero(types[i]);
            }
            args[i] = value;
            validate(p, value, childPath, ctx);
        }
        try {
            Constructor<?> ctor = type.getDeclaredConstructor(types);
            ctor.setAccessible(true);
            return ctor.newInstance(args);
        } catch (ReflectiveOperationException e) {
            Throwable cause = e.getCause() != null ? e.getCause() : e;
            ctx.problems.add(display(path) + ": " + cause.getMessage());
            return defaults;
        }
    }

    private Collection<Object> readCollection(Object raw, Type type, Class<?> target, List<String> path, Ctx ctx) {
        Type element = typeArg(type, 0);
        Collection<Object> out;
        Class<?> elementClass = rawClass(element);
        if (Set.class.isAssignableFrom(target)) {
            out = elementClass.isEnum() ? enumSet(elementClass) : new LinkedHashSet<>();
        } else {
            out = new ArrayList<>();
        }
        Collection<?> items = raw instanceof Collection<?> c ? c : List.of(raw); // a lone scalar becomes a one-item list
        int i = 0;
        for (Object item : items) {
            Object v = read(item, element, child(path, String.valueOf(i++)), ctx);
            if (v != null) {
                out.add(v);
            }
        }
        return out;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private Map<Object, Object> readMap(Object raw, Type type, Class<?> target, List<String> path, Ctx ctx) {
        if (!(raw instanceof Map<?, ?> m)) {
            throw new IllegalArgumentException("expected a section, got '" + raw + "'");
        }
        Type keyType = typeArg(type, 0);
        Type valueType = typeArg(type, 1);
        Class<?> keyClass = rawClass(keyType);
        Map<Object, Object> out = keyClass.isEnum() ? new EnumMap(keyClass) : new LinkedHashMap<>();
        for (Map.Entry<?, ?> e : m.entrySet()) {
            List<String> childPath = child(path, String.valueOf(e.getKey()));
            Object k = read(String.valueOf(e.getKey()), keyType, childPath, ctx);
            Object v = read(e.getValue(), valueType, childPath, ctx);
            if (k != null && v != null) {
                out.put(k, v);
            }
        }
        return out;
    }

    private static Boolean readBoolean(Object raw) {
        if (raw instanceof Boolean b) {
            return b;
        }
        return switch (raw.toString().trim().toLowerCase(Locale.ROOT)) {
            case "true", "yes", "on", "1" -> true;
            case "false", "no", "off", "0" -> false;
            default -> throw new IllegalArgumentException("expected true/false, got '" + raw + "'");
        };
    }

    private static Number readNumber(Object raw, Class<?> target) {
        BigDecimal value;
        try {
            value = raw instanceof Number n ? new BigDecimal(n.toString()) : new BigDecimal(raw.toString().trim().replace("_", ""));
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("expected a number, got '" + raw + "'");
        }
        try {
            if (target == Integer.class) return value.intValueExact();
            if (target == Long.class) return value.longValueExact();
            if (target == Short.class) return value.shortValueExact();
            if (target == Byte.class) return value.byteValueExact();
        } catch (ArithmeticException e) {
            throw new IllegalArgumentException("expected a whole number in range for " + target.getSimpleName() + ", got " + raw);
        }
        if (target == Double.class) return value.doubleValue();
        if (target == Float.class) return value.floatValue();
        if (target == BigInteger.class) return value.toBigIntegerExact();
        if (target == BigDecimal.class || target == Number.class) return value;
        throw new IllegalArgumentException("unsupported number type " + target.getSimpleName());
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static Enum<?> readEnum(Object raw, Class<? extends Enum> type) {
        String name = raw.toString().trim().replace('-', '_').replace(' ', '_');
        for (Enum<?> constant : type.getEnumConstants()) {
            if (constant.name().equalsIgnoreCase(name)) {
                return constant;
            }
        }
        throw new IllegalArgumentException("'" + raw + "' is not one of " + enumNames(type));
    }

    private static Keyed readKeyed(Object raw, Class<? extends Keyed> type) {
        NamespacedKey key = NamespacedKey.fromString(raw.toString().trim().toLowerCase(Locale.ROOT));
        if (key == null) {
            throw new IllegalArgumentException("'" + raw + "' is not a valid key");
        }
        Registry<? extends Keyed> registry = RegistryAccess.registryAccess().getRegistry(type);
        Keyed value = registry.get(key);
        if (value == null) {
            throw new IllegalArgumentException("unknown " + type.getSimpleName() + " '" + key.asString() + "'");
        }
        return value;
    }

    // ================================================================== validation

    private static void validate(Property p, @Nullable Object value, List<String> path, Ctx ctx) {
        if (p.notBlank && (value == null || value.toString().isBlank())) {
            ctx.problems.add(display(path) + ": must not be blank");
        }
        if (p.range != null && value instanceof Number n) {
            double d = n.doubleValue();
            if (d < p.range.min() || d > p.range.max()) {
                ctx.problems.add(display(path) + ": " + n + " is outside " + fmt(p.range.min()) + ".." + fmt(p.range.max()));
            }
        }
        if (p.size != null && value != null) {
            int size = value instanceof Collection<?> c ? c.size()
                    : value instanceof Map<?, ?> m ? m.size()
                    : value instanceof CharSequence s ? s.length() : -1;
            if (size >= 0 && (size < p.size.min() || size > p.size.max())) {
                ctx.problems.add(display(path) + ": size " + size + " is outside " + p.size.min() + ".." + p.size.max());
            }
        }
    }

    private static String fmt(double d) {
        if (d == Double.MAX_VALUE) return "inf";
        if (d == -Double.MAX_VALUE) return "-inf";
        return d == Math.rint(d) ? String.valueOf((long) d) : String.valueOf(d);
    }

    // ================================================================== reflection

    private static final Map<Class<?>, List<Property>> PROPERTIES = new ConcurrentHashMap<>();

    private static final class Property {
        final String key;
        final Type type;
        final Class<?> rawType;
        final Field field;
        final @Nullable Range range;
        final @Nullable Size size;
        final boolean notBlank;
        final String @Nullable [] comment;

        Property(Field field) {
            this.field = field;
            field.setAccessible(true);
            com.enderbyte.enderlib.config.Key key = field.getAnnotation(com.enderbyte.enderlib.config.Key.class);
            this.key = key != null ? key.value() : kebab(field.getName());
            this.type = field.getGenericType();
            this.rawType = field.getType();
            this.range = field.getAnnotation(Range.class);
            this.size = field.getAnnotation(Size.class);
            this.notBlank = field.isAnnotationPresent(NotBlank.class);
            Comment c = field.getAnnotation(Comment.class);
            this.comment = c == null ? null : c.value();
        }

        Object get(Object owner) {
            try {
                return field.get(owner);
            } catch (IllegalAccessException e) {
                throw new IllegalStateException(e);
            }
        }

        void set(Object owner, @Nullable Object value) {
            try {
                field.set(owner, value);
            } catch (IllegalAccessException e) {
                throw new IllegalStateException(e);
            }
        }
    }

    private static List<Property> properties(Class<?> type) {
        return PROPERTIES.computeIfAbsent(type, t -> {
            List<Class<?>> chain = new ArrayList<>();
            for (Class<?> c = t; c != null && c != Object.class && c != Record.class; c = c.getSuperclass()) {
                chain.add(0, c);
            }
            List<Property> out = new ArrayList<>();
            for (Class<?> c : chain) {
                if (c.isRecord()) {
                    for (RecordComponent rc : c.getRecordComponents()) {
                        try {
                            out.add(new Property(c.getDeclaredField(rc.getName())));
                        } catch (NoSuchFieldException e) {
                            throw new IllegalStateException(e);
                        }
                    }
                    continue;
                }
                for (Field f : c.getDeclaredFields()) {
                    int mod = f.getModifiers();
                    if (Modifier.isStatic(mod) || Modifier.isTransient(mod) || f.isSynthetic()) {
                        continue;
                    }
                    out.add(new Property(f));
                }
            }
            return List.copyOf(out);
        });
    }

    private void collectComments(Class<?> type, List<String> path, Map<List<String>, List<String>> out, Set<Class<?>> seen) {
        if (!isBindable(type) || !seen.add(type)) {
            return;
        }
        for (Property p : properties(type)) {
            List<String> childPath = child(path, p.key);
            if (p.comment != null) {
                out.put(childPath, List.of(p.comment));
            }
            collectComments(p.rawType, childPath, out, seen);
        }
        seen.remove(type);
    }

    private boolean isBindable(Class<?> type) {
        if (type.isPrimitive() || type.isEnum() || type.isArray() || type.isInterface()
                || Modifier.isAbstract(type.getModifiers()) || serializerFor(type) != null) {
            return false;
        }
        String name = type.getName();
        if (name.startsWith("java.") || name.startsWith("javax.") || name.startsWith("org.bukkit.")
                || name.startsWith("io.papermc.") || name.startsWith("net.kyori.")) {
            return false;
        }
        return type.isRecord() || hasNoArgConstructor(type);
    }

    private static boolean hasNoArgConstructor(Class<?> type) {
        try {
            type.getDeclaredConstructor();
            return true;
        } catch (NoSuchMethodException e) {
            return false;
        }
    }

    private static Object instantiate(Class<?> type) {
        try {
            Constructor<?> ctor = type.getDeclaredConstructor();
            ctor.setAccessible(true);
            return ctor.newInstance();
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(type.getName() + " needs an accessible no-argument constructor", e);
        }
    }

    /** {@code defaults()} method or {@code DEFAULT}/{@code DEFAULTS} constant of a record, if any. */
    private static @Nullable Object recordDefaults(Class<?> type) {
        try {
            Method m = type.getDeclaredMethod("defaults");
            if (Modifier.isStatic(m.getModifiers()) && type.isAssignableFrom(m.getReturnType())) {
                m.setAccessible(true);
                return m.invoke(null);
            }
        } catch (NoSuchMethodException ignored) {
            // try the constants
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("defaults() of " + type.getName() + " failed", e);
        }
        for (String name : new String[] {"DEFAULT", "DEFAULTS"}) {
            try {
                Field f = type.getDeclaredField(name);
                if (Modifier.isStatic(f.getModifiers()) && type.isAssignableFrom(f.getType())) {
                    f.setAccessible(true);
                    return f.get(null);
                }
            } catch (NoSuchFieldException ignored) {
                // next
            } catch (IllegalAccessException e) {
                throw new IllegalStateException(e);
            }
        }
        return null;
    }

    private @Nullable TypeSerializer<?> serializerFor(Class<?> type) {
        TypeSerializer<?> s = exact.get(type);
        if (s != null) {
            return s;
        }
        synchronized (this) {
            for (Map.Entry<Class<?>, TypeSerializer<?>> e : hierarchy) {
                if (e.getKey().isAssignableFrom(type)) {
                    return e.getValue();
                }
            }
        }
        return null;
    }

    // ================================================================== small helpers

    /** {@code maxPlayers -> max-players}, {@code URLPath -> url-path}. */
    static String kebab(String name) {
        StringBuilder sb = new StringBuilder(name.length() + 4);
        for (int i = 0; i < name.length(); i++) {
            char c = name.charAt(i);
            if (Character.isUpperCase(c)) {
                boolean prevLower = i > 0 && !Character.isUpperCase(name.charAt(i - 1));
                boolean nextLower = i + 1 < name.length() && Character.isLowerCase(name.charAt(i + 1));
                if (i > 0 && (prevLower || nextLower) && sb.charAt(sb.length() - 1) != '-') {
                    sb.append('-');
                }
                sb.append(Character.toLowerCase(c));
            } else if (c == '_') {
                sb.append('-');
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    static Class<?> rawClass(Type type) {
        if (type instanceof Class<?> c) return c;
        if (type instanceof ParameterizedType p) return (Class<?>) p.getRawType();
        if (type instanceof GenericArrayType g) return java.lang.reflect.Array.newInstance(rawClass(g.getGenericComponentType()), 0).getClass();
        if (type instanceof WildcardType w) return rawClass(w.getUpperBounds()[0]);
        return Object.class;
    }

    private static Type typeArg(Type type, int index) {
        if (type instanceof ParameterizedType p && p.getActualTypeArguments().length > index) {
            Type arg = p.getActualTypeArguments()[index];
            return arg instanceof WildcardType w ? w.getUpperBounds()[0] : arg;
        }
        return Object.class;
    }

    private static Class<?> boxed(Class<?> c) {
        if (!c.isPrimitive()) return c;
        if (c == int.class) return Integer.class;
        if (c == long.class) return Long.class;
        if (c == double.class) return Double.class;
        if (c == float.class) return Float.class;
        if (c == boolean.class) return Boolean.class;
        if (c == short.class) return Short.class;
        if (c == byte.class) return Byte.class;
        if (c == char.class) return Character.class;
        return c;
    }

    private static Object primitiveZero(Class<?> c) {
        if (c == boolean.class) return false;
        if (c == char.class) return '\0';
        if (c == long.class) return 0L;
        if (c == double.class) return 0d;
        if (c == float.class) return 0f;
        if (c == short.class) return (short) 0;
        if (c == byte.class) return (byte) 0;
        return 0;
    }

    private static boolean isScalar(Object v) {
        return v instanceof String || v instanceof Number || v instanceof Boolean;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static Collection<Object> enumSet(Class<?> type) {
        return (Collection) EnumSet.noneOf((Class<Enum>) type);
    }

    @SuppressWarnings("rawtypes")
    private static String enumNames(Class<? extends Enum> type) {
        List<String> names = new ArrayList<>();
        for (Enum<?> e : type.getEnumConstants()) {
            names.add(e.name().toLowerCase(Locale.ROOT).replace('_', '-'));
            if (names.size() == 12) {
                names.add("...");
                break;
            }
        }
        return names.toString();
    }

    private static List<String> child(List<String> path, String key) {
        List<String> out = new ArrayList<>(path.size() + 1);
        out.addAll(path);
        out.add(key);
        return out;
    }

    private static String display(List<String> path) {
        return path.isEmpty() ? "<root>" : String.join(".", path);
    }
}
