package com.enderbyte.enderlib.menu;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ClickThrottleTest {

    @Test
    void firstClickIsAlwaysAllowed() {
        // Regression: now - Integer.MIN_VALUE overflowed and rejected every click forever
        assertThat(MenuView.clickAllowed(0, Integer.MIN_VALUE, 2)).isTrue();
        assertThat(MenuView.clickAllowed(123_456, Integer.MIN_VALUE, 2)).isTrue();
        assertThat(MenuView.clickAllowed(Integer.MAX_VALUE, Integer.MIN_VALUE, 2)).isTrue();
    }

    @Test
    void throttlesWithinTheDelay() {
        assertThat(MenuView.clickAllowed(100, 100, 2)).isFalse();
        assertThat(MenuView.clickAllowed(101, 100, 2)).isFalse();
        assertThat(MenuView.clickAllowed(102, 100, 2)).isTrue();
        assertThat(MenuView.clickAllowed(100, 100, 0)).isTrue();
    }
}
