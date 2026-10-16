package com.enderbyte.enderlib.database.migration;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * Content checksums for migrations.
 *
 * <p>Line endings and trailing whitespace are normalised first. Without that, checking the same
 * repository out on Windows and on Linux produces different checksums for identical files, and the
 * second server to start reports a corrupted migration history that is not actually corrupted.
 */
final class Checksums {

    private Checksums() {
    }

    /**
     * @param content migration content
     * @return a hex SHA-256 of the normalised content
     */
    static String of(final String content) {
        final byte[] digest = sha256(normalise(content).getBytes(StandardCharsets.UTF_8));
        return HexFormat.of().formatHex(digest);
    }

    private static String normalise(final String content) {
        final String[] lines = content.replace("\r\n", "\n").replace('\r', '\n').split("\n", -1);
        final StringBuilder normalised = new StringBuilder(content.length());
        for (final String line : lines) {
            normalised.append(stripTrailing(line)).append('\n');
        }
        // Collapse the blank lines a file may or may not end with.
        while (normalised.length() > 0 && normalised.charAt(normalised.length() - 1) == '\n') {
            normalised.setLength(normalised.length() - 1);
        }
        return normalised.toString();
    }

    private static String stripTrailing(final String line) {
        int end = line.length();
        while (end > 0 && Character.isWhitespace(line.charAt(end - 1))) {
            end--;
        }
        return line.substring(0, end);
    }

    private static byte[] sha256(final byte[] input) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(input);
        } catch (final NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is required by every Java platform", e);
        }
    }
}
