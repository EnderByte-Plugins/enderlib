package com.enderbyte.enderlib.example;

import com.enderbyte.enderlib.config.Comment;
import com.enderbyte.enderlib.config.Header;
import com.enderbyte.enderlib.config.Range;

import java.time.Duration;

/** Bound to {@code config.yml}; missing keys are written with these defaults. */
@Header("EnderLib example settings")
public final class Settings {

    @Comment("How long the demo hologram stays")
    public Duration hologramLifetime = Duration.ofMinutes(5);

    public DatabaseSettings database = new DatabaseSettings();

    public static final class DatabaseSettings {
        @Comment("Connections in the pool")
        @Range(min = 1, max = 32)
        public int poolSize = 4;
    }
}
