package com.enderbyte.enderlib.config;

import java.util.List;

/**
 * A config file could not be read or failed validation. The message lists every problem with its
 * path, so a server owner can fix them all in one go.
 */
public class ConfigException extends RuntimeException {

    private final List<String> problems;

    public ConfigException(String file, List<String> problems) {
        super("Invalid config '" + file + "':\n  - " + String.join("\n  - ", problems));
        this.problems = List.copyOf(problems);
    }

    public ConfigException(String message, Throwable cause) {
        super(message, cause);
        this.problems = List.of(message);
    }

    public List<String> problems() {
        return problems;
    }
}
