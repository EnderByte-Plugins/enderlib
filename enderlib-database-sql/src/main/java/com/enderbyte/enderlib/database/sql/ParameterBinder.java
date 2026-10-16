package com.enderbyte.enderlib.database.sql;

import com.enderbyte.enderlib.database.api.mapping.ConverterRegistry;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Types;
import java.util.List;

/**
 * Binds values to a {@link PreparedStatement}.
 *
 * <p>Every value the library sends to a database passes through here, and it always uses
 * {@code setObject}/{@code setNull} against a placeholder. There is no code path anywhere in the
 * library that concatenates a value into SQL text.
 */
final class ParameterBinder {

    private ParameterBinder() {
    }

    /**
     * @param statement  the statement to bind to
     * @param parameters values in placeholder order
     * @param converters registry that turns Java values into driver-friendly ones
     * @throws SQLException if the driver rejects a value
     */
    static void bind(final PreparedStatement statement,
                     final List<Object> parameters,
                     final ConverterRegistry converters) throws SQLException {
        for (int i = 0; i < parameters.size(); i++) {
            final Object converted = converters.toDatabase(parameters.get(i));
            if (converted == null) {
                statement.setNull(i + 1, Types.NULL);
            } else {
                statement.setObject(i + 1, converted);
            }
        }
    }
}
