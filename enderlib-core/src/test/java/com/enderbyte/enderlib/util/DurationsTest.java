package com.enderbyte.enderlib.util;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DurationsTest {

    @Test
    void parsesCompoundDurations() {
        assertThat(Durations.parse("1d2h30m")).isEqualTo(Duration.ofDays(1).plusHours(2).plusMinutes(30));
        assertThat(Durations.parse("1h 15s")).isEqualTo(Duration.ofHours(1).plusSeconds(15));
        assertThat(Durations.parse("2w")).isEqualTo(Duration.ofDays(14));
        assertThat(Durations.parse("500ms")).isEqualTo(Duration.ofMillis(500));
        assertThat(Durations.parse("20t")).isEqualTo(Duration.ofSeconds(1));
        assertThat(Durations.parse("90")).isEqualTo(Duration.ofSeconds(90));
        assertThat(Durations.parse("5M")).isEqualTo(Duration.ofMinutes(5));
    }

    @Test
    void rejectsGarbage() {
        assertThatThrownBy(() -> Durations.parse("")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Durations.parse("abc")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Durations.parse("5x")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Durations.parse("5m garbage")).isInstanceOf(IllegalArgumentException.class);
        assertThat(Durations.parseOr("nope", Duration.ZERO)).isZero();
    }

    @Test
    void formatsAndRoundTrips() {
        Duration d = Duration.ofDays(1).plusHours(2).plusMinutes(3).plusSeconds(4);
        assertThat(Durations.format(d)).isEqualTo("1d 2h 3m 4s");
        assertThat(Durations.parse(Durations.toCompact(d))).isEqualTo(d);
        assertThat(Durations.format(Duration.ZERO)).isEqualTo("0s");
        assertThat(Durations.format(Duration.ofMillis(250))).isEqualTo("250ms");
        assertThat(Durations.clock(Duration.ofSeconds(75))).isEqualTo("01:15");
        assertThat(Durations.clock(Duration.ofSeconds(3675))).isEqualTo("1:01:15");
    }
}
