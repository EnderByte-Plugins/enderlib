package com.enderbyte.enderlib.menu.layout;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * A menu layout drawn as text rows, one character per slot.
 *
 * <pre>{@code
 * SlotPattern.of(9,
 *         "#########",
 *         "#.......#",
 *         "#.......#",
 *         "#<##X##>#");
 * }</pre>
 *
 * Spaces are ordinary characters (often used for "leave empty"). Rows shorter than the width leave
 * the remaining slots unassigned.
 */
public final class SlotPattern {

    private final int width;
    private final String[] rows;
    private final Map<Character, int[]> slots;

    private SlotPattern(int width, String[] rows) {
        this.width = width;
        this.rows = rows.clone();
        Map<Character, List<Integer>> collected = new LinkedHashMap<>();
        for (int row = 0; row < rows.length; row++) {
            String line = rows[row];
            if (line.length() > width) {
                throw new IllegalArgumentException("Pattern row " + (row + 1) + " is " + line.length()
                        + " characters wide, the menu is " + width + ": \"" + line + "\"");
            }
            for (int col = 0; col < line.length(); col++) {
                collected.computeIfAbsent(line.charAt(col), c -> new ArrayList<>()).add(row * width + col);
            }
        }
        Map<Character, int[]> out = new LinkedHashMap<>();
        collected.forEach((c, list) -> out.put(c, list.stream().mapToInt(Integer::intValue).toArray()));
        this.slots = Collections.unmodifiableMap(out);
    }

    /** Pattern for a chest menu (9 columns). */
    public static SlotPattern of(String... rows) {
        return of(Slots.COLUMNS, rows);
    }

    /** Pattern for a menu {@code width} columns wide (5 for hoppers, 3 for droppers). */
    public static SlotPattern of(int width, String... rows) {
        if (rows.length == 0) {
            throw new IllegalArgumentException("Pattern needs at least one row");
        }
        return new SlotPattern(width, rows);
    }

    /** Slots of {@code c} in reading order (left to right, top to bottom). Empty if absent. */
    public int[] slots(char c) {
        int[] s = slots.get(c);
        return s == null ? new int[0] : s.clone();
    }

    public boolean contains(char c) {
        return slots.containsKey(c);
    }

    public Optional<Character> charAt(int slot) {
        int row = slot / width;
        int col = slot % width;
        if (row < 0 || row >= rows.length || col >= rows[row].length()) {
            return Optional.empty();
        }
        return Optional.of(rows[row].charAt(col));
    }

    public Set<Character> characters() {
        return slots.keySet();
    }

    public int rows() {
        return rows.length;
    }

    public int width() {
        return width;
    }

    @Override
    public String toString() {
        return "SlotPattern" + Arrays.toString(rows);
    }
}
