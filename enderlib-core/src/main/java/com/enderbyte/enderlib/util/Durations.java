package com.enderbyte.enderlib.util;

import java.time.Duration;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Human duration parsing and formatting: {@code "1d2h30m"}, {@code "90s"}, {@code "500ms"}, {@code "2w"}.
 */
public final class Durations {

    private static final Pattern PART = Pattern.compile("(\\d+)\\s*(ms|w|d|h|m|s|t)", Pattern.CASE_INSENSITIVE);

    private Durations() {
    }

    /**
     * Parses a compact duration. Units: {@code w d h m s ms}, and {@code t} for game ticks (50 ms).
     * A bare number is read as seconds.
     *
     * @throws IllegalArgumentException if the input is not a valid duration
     */
    public static Duration parse(String input) {
        String s = input.trim().toLowerCase(Locale.ROOT);
        if (s.isEmpty()) {
            throw new IllegalArgumentException("Empty duration");
        }
        if (s.chars().allMatch(Character::isDigit)) {
            return Duration.ofSeconds(Long.parseLong(s));
        }
        Matcher m = PART.matcher(s);
        Duration total = Duration.ZERO;
        int consumed = 0;
        while (m.find()) {
            if (!s.substring(consumed, m.start()).isBlank()) {
                throw new IllegalArgumentException("Invalid duration: " + input);
            }
            long n = Long.parseLong(m.group(1));
            total = total.plus(switch (m.group(2)) {
                case "ms" -> Duration.ofMillis(n);
                case "w" -> Duration.ofDays(n * 7);
                case "d" -> Duration.ofDays(n);
                case "h" -> Duration.ofHours(n);
                case "m" -> Duration.ofMinutes(n);
                case "s" -> Duration.ofSeconds(n);
                case "t" -> Duration.ofMillis(n * 50);
                default -> throw new IllegalArgumentException("Invalid unit in " + input);
            });
            consumed = m.end();
        }
        if (consumed == 0 || !s.substring(consumed).isBlank()) {
            throw new IllegalArgumentException("Invalid duration: " + input);
        }
        return total;
    }

    /** Parses, returning {@code fallback} on bad input. */
    public static Duration parseOr(String input, Duration fallback) {
        try {
            return parse(input);
        } catch (IllegalArgumentException e) {
            return fallback;
        }
    }

    /** Compact form: {@code 1d 2h 3m 4s}. Sub-second durations are shown in milliseconds. */
    public static String format(Duration duration) {
        if (duration.isNegative() || duration.isZero()) {
            return "0s";
        }
        if (duration.compareTo(Duration.ofSeconds(1)) < 0) {
            return duration.toMillis() + "ms";
        }
        long days = duration.toDays();
        int hours = duration.toHoursPart();
        int minutes = duration.toMinutesPart();
        int seconds = duration.toSecondsPart();
        StringBuilder sb = new StringBuilder();
        append(sb, days, "d");
        append(sb, hours, "h");
        append(sb, minutes, "m");
        append(sb, seconds, "s");
        return sb.toString();
    }

    /** Form suited to config files and round-tripping through {@link #parse}: {@code 1d2h3m4s}. */
    public static String toCompact(Duration duration) {
        String formatted = format(duration).replace(" ", "");
        return formatted;
    }

    /** {@code mm:ss} or {@code h:mm:ss}, for countdowns. */
    public static String clock(Duration duration) {
        long total = Math.max(0, duration.toSeconds());
        long h = total / 3600;
        long m = (total % 3600) / 60;
        long s = total % 60;
        return h > 0 ? String.format(Locale.ROOT, "%d:%02d:%02d", h, m, s) : String.format(Locale.ROOT, "%02d:%02d", m, s);
    }

    private static void append(StringBuilder sb, long value, String unit) {
        if (value > 0) {
            if (!sb.isEmpty()) {
                sb.append(' ');
            }
            sb.append(value).append(unit);
        }
    }
}
