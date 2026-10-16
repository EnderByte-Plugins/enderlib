package com.enderbyte.enderlib.text;

/**
 * Pixel widths of the default Minecraft font, for centering and padding text.
 */
public final class FontWidth {

    /** Width of the chat box in pixels at default settings. */
    public static final int CHAT_WIDTH = 320;
    /** Width of a space including its 1px gap. */
    public static final int SPACE = 4;

    private FontWidth() {
    }

    /** Width of a single character in pixels, including the 1px gap after it. */
    public static int of(char c, boolean bold) {
        int base = switch (c) {
            case 'i', '!', ',', '.', ':', ';', '|', '\'' -> 1;
            case 'l', '`' -> 2;
            case 'I', 't', '[', ']', ' ' -> 3;
            case 'f', 'k', '<', '>', '(', ')', '{', '}', '"', '*' -> 4;
            case '@', '~' -> 6;
            default -> 5;
        };
        return base + 1 + (bold && c != ' ' ? 1 : 0);
    }

    /** Width of a string in pixels. */
    public static int of(String text, boolean bold) {
        int width = 0;
        for (int i = 0; i < text.length(); i++) {
            width += of(text.charAt(i), bold);
        }
        return width;
    }
}
