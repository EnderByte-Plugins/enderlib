package com.enderbyte.enderlib.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ConfigHandleTest {

    @Header("Test settings")
    static final class Settings {
        @Comment("How many homes")
        int maxHomes = 3;
        String greeting = "hello";
    }

    @TempDir
    Path dir;

    @Test
    void createsFileWithDefaultsCommentsAndHeader() throws IOException {
        File file = dir.resolve("config.yml").toFile();
        ConfigHandle<Settings> handle = ConfigHandle.open(file, Settings.class, ConfigMapper.standard());

        assertThat(handle.get().maxHomes).isEqualTo(3);
        String yaml = Files.readString(file.toPath());
        assertThat(yaml).contains("# Test settings").contains("# How many homes").contains("max-homes: 3").contains("greeting: hello");
    }

    @Test
    void addsOnlyMissingKeysAndKeepsUserComments() throws IOException {
        Path file = dir.resolve("config.yml");
        Files.writeString(file, "# my own note\nmax-homes: 10\n");

        ConfigHandle<Settings> handle = ConfigHandle.open(file.toFile(), Settings.class, ConfigMapper.standard());

        assertThat(handle.get().maxHomes).isEqualTo(10);
        String yaml = Files.readString(file);
        assertThat(yaml).contains("# my own note").contains("max-homes: 10").contains("greeting: hello");
    }

    @Test
    void failedReloadKeepsPreviousValue() throws IOException {
        Path file = dir.resolve("config.yml");
        ConfigHandle<Settings> handle = ConfigHandle.open(file.toFile(), Settings.class, ConfigMapper.standard());
        Files.writeString(file, "max-homes: lots\n");

        assertThatThrownBy(handle::reload).isInstanceOf(ConfigException.class).hasMessageContaining("max-homes");
        assertThat(handle.get().maxHomes).isEqualTo(3);

        Files.writeString(file, "max-homes: 12\ngreeting: hi\n");
        int[] seen = {0};
        handle.onReload(s -> seen[0] = s.maxHomes);
        handle.reload();
        assertThat(seen[0]).isEqualTo(12);
    }

    @Test
    void saveWritesCurrentValue() throws IOException {
        Path file = dir.resolve("config.yml");
        ConfigHandle<Settings> handle = ConfigHandle.open(file.toFile(), Settings.class, ConfigMapper.standard());
        handle.get().maxHomes = 42;
        handle.save();
        assertThat(Files.readString(file)).contains("max-homes: 42").contains("# How many homes");
    }
}
