package com.enderbyte.enderlib.config;

import net.kyori.adventure.text.Component;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class ConfigMapperTest {

    enum Mode { SURVIVAL, HARD_CORE }

    static final class Settings {
        @Comment("Max homes per player")
        @Range(min = 1, max = 50)
        int maxHomes = 3;
        Duration teleportDelay = Duration.ofSeconds(3);
        @Key("motd") Component message = Component.text("hi");
        Mode mode = Mode.SURVIVAL;
        Set<Mode> allowed = EnumSet.of(Mode.SURVIVAL);
        List<String> worlds = new ArrayList<>(List.of("world"));
        Map<String, Integer> limits = new LinkedHashMap<>(Map.of("vip", 10));
        Database database = new Database();
        Optional<String> nickname = Optional.empty();
        @NotBlank String serverName = "lobby";
    }

    static final class Database {
        String host = "localhost";
        int port = 5432;
    }

    record Reward(String item, int amount) {
        static Reward defaults() {
            return new Reward("diamond", 1);
        }
    }

    record Strict(String id, int weight) {
    }

    private final ConfigMapper mapper = ConfigMapper.standard();

    @Test
    void bindsValuesAndKeepsDefaultsForMissingKeys() {
        Map<String, Object> tree = new LinkedHashMap<>();
        tree.put("max-homes", 7);
        tree.put("teleport-delay", "1m30s");
        tree.put("motd", "<red>Hello");
        tree.put("mode", "hard-core");
        tree.put("allowed", List.of("survival", "HARD_CORE"));
        tree.put("limits", Map.of("mvp", "25"));
        tree.put("database", Map.of("port", 3306));
        tree.put("nickname", "Ender");

        ConfigMapper.Binding<Settings> b = mapper.bind(tree, Settings.class);

        assertThat(b.problems()).isEmpty();
        Settings s = b.value();
        assertThat(s.maxHomes).isEqualTo(7);
        assertThat(s.teleportDelay).isEqualTo(Duration.ofSeconds(90));
        assertThat(s.mode).isEqualTo(Mode.HARD_CORE);
        assertThat(s.allowed).containsExactlyInAnyOrder(Mode.SURVIVAL, Mode.HARD_CORE);
        assertThat(s.limits).containsEntry("mvp", 25);
        assertThat(s.database.port).isEqualTo(3306);
        assertThat(s.database.host).isEqualTo("localhost");
        assertThat(s.nickname).contains("Ender");
        assertThat(s.worlds).containsExactly("world");
        assertThat(b.missing()).contains(List.of("worlds"), List.of("database", "host"), List.of("server-name"));
    }

    @Test
    void collectsEveryProblemWithItsPath() {
        Map<String, Object> tree = Map.of(
                "max-homes", 500,
                "mode", "creative",
                "database", Map.of("port", "not-a-port"),
                "server-name", "  ");

        ConfigMapper.Binding<Settings> b = mapper.bind(tree, Settings.class);

        assertThat(b.problems()).hasSize(4);
        assertThat(String.join("\n", b.problems()))
                .contains("max-homes: 500 is outside 1..50")
                .contains("mode: 'creative' is not one of")
                .contains("database.port: expected a number")
                .contains("server-name: must not be blank");
    }

    @Test
    void recordsUseDefaultsMethodOrRequireAllComponents() {
        ConfigMapper.Binding<Reward> reward = mapper.bind(Map.of("amount", 4), Reward.class);
        assertThat(reward.value()).isEqualTo(new Reward("diamond", 4));
        assertThat(reward.missing()).containsExactly(List.of("item"));

        ConfigMapper.Binding<Strict> strict = mapper.bind(Map.of("id", "x"), Strict.class);
        assertThat(strict.problems()).containsExactly("weight: required");
    }

    @Test
    @SuppressWarnings("unchecked")
    void writesAPlainTreeThatReadsBackIdentically() {
        Settings original = new Settings();
        original.maxHomes = 9;
        original.mode = Mode.HARD_CORE;
        Map<String, Object> tree = (Map<String, Object>) mapper.write(original, Settings.class);

        assertThat(tree).containsEntry("max-homes", 9).containsEntry("mode", "HARD_CORE").containsEntry("teleport-delay", "3s");
        assertThat((Map<String, Object>) tree.get("database")).containsEntry("port", 5432);

        Settings copy = mapper.bind(tree, Settings.class).value();
        assertThat(copy.maxHomes).isEqualTo(9);
        assertThat(copy.mode).isEqualTo(Mode.HARD_CORE);
        assertThat(copy.limits).isEqualTo(original.limits);
    }

    @Test
    void customSerializersWin() {
        mapper.register(Database.class, TypeSerializer.of(raw -> {
            String[] parts = raw.toString().split(":");
            Database d = new Database();
            d.host = parts[0];
            d.port = Integer.parseInt(parts[1]);
            return d;
        }, d -> d.host + ":" + d.port));
        Settings s = mapper.bind(Map.of("database", "db.example:6000"), Settings.class).value();
        assertThat(s.database.host).isEqualTo("db.example");
        assertThat(s.database.port).isEqualTo(6000);
    }

    @Test
    void commentsAndKebabCase() {
        assertThat(mapper.comments(Settings.class)).containsEntry(List.of("max-homes"), List.of("Max homes per player"));
        assertThat(ConfigMapper.kebab("maxHomes")).isEqualTo("max-homes");
        assertThat(ConfigMapper.kebab("URLPath")).isEqualTo("url-path");
        assertThat(ConfigMapper.kebab("snake_case")).isEqualTo("snake-case");
    }
}
