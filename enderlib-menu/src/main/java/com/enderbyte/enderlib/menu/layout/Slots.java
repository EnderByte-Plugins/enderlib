package com.enderbyte.enderlib.menu.layout;

import java.util.Arrays;
import java.util.stream.IntStream;

/**
 * Slot arithmetic for chest-style grids. Rows and columns are zero-based.
 */
public final class Slots {

    public static final int COLUMNS = 9;
    public static final int MAX_ROWS = 6;

    private Slots() {
    }

    public static int slot(int row, int column) {
        return row * COLUMNS + column;
    }

    public static int row(int slot) {
        return slot / COLUMNS;
    }

    public static int column(int slot) {
        return slot % COLUMNS;
    }

    public static int clampRows(int rows) {
        return Math.max(1, Math.min(MAX_ROWS, rows));
    }

    /** Every slot of a menu with {@code rows} rows. */
    public static int[] all(int rows) {
        return IntStream.range(0, rows * COLUMNS).toArray();
    }

    /** All slots of {@code row}. */
    public static int[] rowSlots(int row) {
        return rect(row, 0, row, COLUMNS - 1);
    }

    /** All slots of {@code column} in a menu with {@code rows} rows. */
    public static int[] columnSlots(int column, int rows) {
        return rect(0, column, rows - 1, column);
    }

    /** Inclusive rectangle, in reading order. */
    public static int[] rect(int fromRow, int fromColumn, int toRow, int toColumn) {
        int r0 = Math.min(fromRow, toRow);
        int r1 = Math.max(fromRow, toRow);
        int c0 = Math.min(fromColumn, toColumn);
        int c1 = Math.max(fromColumn, toColumn);
        int[] out = new int[(r1 - r0 + 1) * (c1 - c0 + 1)];
        int i = 0;
        for (int r = r0; r <= r1; r++) {
            for (int c = c0; c <= c1; c++) {
                out[i++] = slot(r, c);
            }
        }
        return out;
    }

    /** The outer ring of a menu with {@code rows} rows. */
    public static int[] border(int rows) {
        return IntStream.range(0, rows * COLUMNS)
                .filter(s -> row(s) == 0 || row(s) == rows - 1 || column(s) == 0 || column(s) == COLUMNS - 1)
                .toArray();
    }

    /** Everything except the outer ring. */
    public static int[] inner(int rows) {
        if (rows < 3) {
            return new int[0];
        }
        return rect(1, 1, rows - 2, COLUMNS - 2);
    }

    /** {@code slots} reordered column by column (top to bottom, then left to right). */
    public static int[] columnMajor(int[] slots, int width) {
        Integer[] boxed = Arrays.stream(slots).boxed().toArray(Integer[]::new);
        Arrays.sort(boxed, (a, b) -> {
            int byColumn = Integer.compare(a % width, b % width);
            return byColumn != 0 ? byColumn : Integer.compare(a / width, b / width);
        });
        return Arrays.stream(boxed).mapToInt(Integer::intValue).toArray();
    }

    /** Width and height of the bounding box of {@code slots}, as {@code {columns, rows}}. */
    public static int[] bounds(int[] slots, int width) {
        if (slots.length == 0) {
            return new int[] {0, 0};
        }
        int minC = Integer.MAX_VALUE, maxC = Integer.MIN_VALUE, minR = Integer.MAX_VALUE, maxR = Integer.MIN_VALUE;
        for (int s : slots) {
            minC = Math.min(minC, s % width);
            maxC = Math.max(maxC, s % width);
            minR = Math.min(minR, s / width);
            maxR = Math.max(maxR, s / width);
        }
        return new int[] {maxC - minC + 1, maxR - minR + 1};
    }
}
