package com.enderbyte.enderlib.util;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;

class CooldownsTest {

    private final AtomicLong now = new AtomicLong(1_000);
    private final Cooldowns<String> cooldowns = Cooldowns.of(Duration.ofSeconds(10), now::get);

    @Test
    void blocksUntilExpiry() {
        assertThat(cooldowns.tryUse("a")).isTrue();
        assertThat(cooldowns.tryUse("a")).isFalse();
        assertThat(cooldowns.remaining("a")).isEqualTo(Duration.ofSeconds(10));
        now.addAndGet(9_999);
        assertThat(cooldowns.isActive("a")).isTrue();
        now.addAndGet(1);
        assertThat(cooldowns.isActive("a")).isFalse();
        assertThat(cooldowns.tryUse("a")).isTrue();
    }

    @Test
    void keysAreIndependentAndCleanupDropsExpired() {
        cooldowns.tryUse("a");
        cooldowns.put("b", Duration.ofSeconds(60));
        now.addAndGet(20_000);
        cooldowns.cleanup();
        assertThat(cooldowns.size()).isEqualTo(1);
        assertThat(cooldowns.isActive("b")).isTrue();
        cooldowns.reset("b");
        assertThat(cooldowns.remaining("b")).isZero();
    }
}
