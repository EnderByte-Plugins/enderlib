package com.enderbyte.enderlib.core;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CompositeTerminableTest {

    @Test
    void closesNewestFirstAndSurvivesFailures() {
        List<String> order = new ArrayList<>();
        CompositeTerminable c = CompositeTerminable.create();
        c.with(() -> order.add("first"));
        c.with((AutoCloseable) () -> { throw new IllegalStateException("boom"); });
        c.with(() -> order.add("last"));
        c.close();
        assertThat(order).containsExactly("last", "first");
        assertThat(c.isClosed()).isTrue();
    }

    @Test
    void lateRegistrationClosesImmediately() {
        CompositeTerminable c = CompositeTerminable.create();
        c.close();
        boolean[] closed = {false};
        c.with(() -> closed[0] = true);
        assertThat(closed[0]).isTrue();
    }
}
