package com.enderbyte.enderlib.command;

import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

/**
 * Parses the flag section of a command line. Pure and server-independent.
 */
public final class FlagParser {

    private FlagParser() {
    }

    /**
     * @param input   the raw text after the last argument
     * @param flags   flags declared on the command
     * @param allowed whether the sender may use a flag (permission check)
     * @return flag name to value ({@code Boolean.TRUE} for switches)
     * @throws IllegalArgumentException with a user-facing message
     */
    public static Map<String, Object> parse(String input, List<Flag<?>> flags, Predicate<Flag<?>> allowed) {
        Map<String, Object> out = new HashMap<>();
        List<String> tokens = tokenize(input);
        for (int i = 0; i < tokens.size(); i++) {
            String token = tokens.get(i);
            if (token.startsWith("--") && token.length() > 2) {
                String body = token.substring(2);
                int eq = body.indexOf('=');
                String name = (eq < 0 ? body : body.substring(0, eq)).toLowerCase(Locale.ROOT);
                Flag<?> flag = byName(flags, name);
                if (flag == null || !allowed.test(flag)) {
                    throw new IllegalArgumentException("Unknown option --" + name);
                }
                if (flag.takesValue()) {
                    String value;
                    if (eq >= 0) {
                        value = body.substring(eq + 1);
                    } else if (i + 1 < tokens.size()) {
                        value = tokens.get(++i);
                    } else {
                        throw new IllegalArgumentException("--" + name + " needs a value");
                    }
                    put(out, flag, value);
                } else {
                    if (eq >= 0) {
                        throw new IllegalArgumentException("--" + name + " takes no value");
                    }
                    out.put(flag.name(), Boolean.TRUE);
                }
            } else if (token.startsWith("-") && token.length() > 1 && !isNumber(token)) {
                String shorts = token.substring(1);
                for (int c = 0; c < shorts.length(); c++) {
                    Flag<?> flag = byShort(flags, shorts.charAt(c));
                    if (flag == null || !allowed.test(flag)) {
                        throw new IllegalArgumentException("Unknown option -" + shorts.charAt(c));
                    }
                    if (flag.takesValue()) {
                        if (c != shorts.length() - 1) {
                            throw new IllegalArgumentException("-" + shorts.charAt(c) + " needs a value and must come last in " + token);
                        }
                        if (i + 1 >= tokens.size()) {
                            throw new IllegalArgumentException("-" + shorts.charAt(c) + " needs a value");
                        }
                        put(out, flag, tokens.get(++i));
                    } else {
                        out.put(flag.name(), Boolean.TRUE);
                    }
                }
            } else {
                throw new IllegalArgumentException("Unexpected '" + token + "'");
            }
        }
        return out;
    }

    /** Flag names worth suggesting for the token being typed. */
    public static List<String> suggest(String input, List<Flag<?>> flags, Predicate<Flag<?>> allowed) {
        List<String> tokens = tokenize(input);
        boolean newToken = input.isEmpty() || Character.isWhitespace(input.charAt(input.length() - 1));
        String current = newToken || tokens.isEmpty() ? "" : tokens.getLast();
        Set<String> used = new LinkedHashSet<>();
        for (String t : tokens) {
            if (t.startsWith("--")) {
                used.add(t.substring(2).split("=", 2)[0]);
            } else if (t.startsWith("-") && !isNumber(t)) {
                for (char c : t.substring(1).toCharArray()) {
                    Flag<?> f = byShort(flags, c);
                    if (f != null) {
                        used.add(f.name());
                    }
                }
            }
        }
        // After a value flag, the next token is its value: nothing to suggest
        String previous = newToken ? (tokens.isEmpty() ? "" : tokens.getLast()) : (tokens.size() > 1 ? tokens.get(tokens.size() - 2) : "");
        Flag<?> pending = previous.startsWith("--") ? byName(flags, previous.substring(2))
                : previous.length() == 2 && previous.startsWith("-") ? byShort(flags, previous.charAt(1)) : null;
        if (pending != null && pending.takesValue() && !previous.contains("=")) {
            return List.of();
        }
        List<String> out = new ArrayList<>();
        for (Flag<?> f : flags) {
            if (!allowed.test(f) || (used.contains(f.name()) && !current.equals("--" + f.name()))) {
                continue;
            }
            String full = "--" + f.name();
            if (full.startsWith(current)) {
                out.add(full);
            }
        }
        return out;
    }

    /** Splits on whitespace; {@code "double quotes"} group words. */
    static List<String> tokenize(String input) {
        List<String> out = new ArrayList<>();
        StringBuilder sb = new StringBuilder();
        boolean quoted = false;
        boolean any = false;
        for (int i = 0; i < input.length(); i++) {
            char c = input.charAt(i);
            if (c == '"') {
                quoted = !quoted;
                any = true;
            } else if (Character.isWhitespace(c) && !quoted) {
                if (any) {
                    out.add(sb.toString());
                    sb.setLength(0);
                    any = false;
                }
            } else {
                sb.append(c);
                any = true;
            }
        }
        if (any) {
            out.add(sb.toString());
        }
        return out;
    }

    private static void put(Map<String, Object> out, Flag<?> flag, String value) {
        try {
            out.put(flag.name(), flag.parse(value));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("--" + flag.name() + ": " + e.getMessage(), e);
        }
    }

    private static @Nullable Flag<?> byName(List<Flag<?>> flags, String name) {
        for (Flag<?> f : flags) {
            if (f.name().equals(name)) {
                return f;
            }
        }
        return null;
    }

    private static @Nullable Flag<?> byShort(List<Flag<?>> flags, char c) {
        for (Flag<?> f : flags) {
            if (f.shortName() != null && f.shortName() == c) {
                return f;
            }
        }
        return null;
    }

    private static boolean isNumber(String token) {
        try {
            Double.parseDouble(token);
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }
}
