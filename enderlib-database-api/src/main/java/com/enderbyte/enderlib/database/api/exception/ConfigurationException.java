package com.enderbyte.enderlib.database.api.exception;

/**
 * Thrown for invalid or incomplete configuration, and when no provider module is present on the
 * classpath for the requested {@link com.enderbyte.enderlib.database.api.config.DatabaseType}.
 *
 * <p>This is always a programming or deployment error, never a transient one, so it is never
 * retried.
 */
public class ConfigurationException extends DatabaseException {

    private static final long serialVersionUID = 1L;

    public ConfigurationException(final String message) {
        super(message);
    }

    public ConfigurationException(final String message, final Throwable cause) {
        super(message, cause);
    }
}
