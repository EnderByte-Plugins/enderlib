package com.enderbyte.enderlib.command;

import com.enderbyte.enderlib.command.annotation.AnnotatedCommands;
import com.enderbyte.enderlib.command.annotation.Arg;
import com.enderbyte.enderlib.command.annotation.Command;
import com.enderbyte.enderlib.command.annotation.Default;
import com.enderbyte.enderlib.command.annotation.Description;
import com.enderbyte.enderlib.command.annotation.Opt;
import com.enderbyte.enderlib.command.annotation.ParameterTypes;
import com.enderbyte.enderlib.command.annotation.Range;
import com.enderbyte.enderlib.command.annotation.Subcommand;
import com.enderbyte.enderlib.util.Durations;
import org.bukkit.command.CommandSender;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CommandTreeTest {

    private static final List<Flag<?>> FLAGS = List.of(
            Flag.of("silent", 's'),
            Flag.of("force", 'f'),
            Flag.value("reason", 'r', s -> s),
            Flag.value("duration", 'd', Durations::parse));

    @Test
    void parsesLongShortCombinedAndValueFlags() {
        Map<String, Object> flags = FlagParser.parse("-sf --reason \"spam bot\" -d 1h", FLAGS, f -> true);
        assertThat(flags).containsEntry("silent", true).containsEntry("force", true)
                .containsEntry("reason", "spam bot").containsEntry("duration", Duration.ofHours(1));
        assertThat(FlagParser.parse("--reason=afk", FLAGS, f -> true)).containsEntry("reason", "afk");
        assertThat(FlagParser.parse("", FLAGS, f -> true)).isEmpty();
    }

    @Test
    void reportsFlagErrors() {
        assertThatThrownBy(() -> FlagParser.parse("--nope", FLAGS, f -> true)).hasMessage("Unknown option --nope");
        assertThatThrownBy(() -> FlagParser.parse("--reason", FLAGS, f -> true)).hasMessage("--reason needs a value");
        assertThatThrownBy(() -> FlagParser.parse("-d soon", FLAGS, f -> true)).hasMessageStartingWith("--duration:");
        assertThatThrownBy(() -> FlagParser.parse("stray", FLAGS, f -> true)).hasMessage("Unexpected 'stray'");
        assertThatThrownBy(() -> FlagParser.parse("--force", FLAGS, f -> !f.name().equals("force")))
                .hasMessage("Unknown option --force");
    }

    @Test
    void suggestsUnusedFlags() {
        assertThat(FlagParser.suggest("", FLAGS, f -> true)).containsExactly("--silent", "--force", "--reason", "--duration");
        assertThat(FlagParser.suggest("--s", FLAGS, f -> true)).containsExactly("--silent");
        assertThat(FlagParser.suggest("--silent ", FLAGS, f -> true)).doesNotContain("--silent");
        assertThat(FlagParser.suggest("--reason ", FLAGS, f -> true)).isEmpty(); // a value is expected
    }

    @Test
    void usageLinesFollowTheTree() {
        LiteralNode root = Cmd.command("eco").withHelp().description("Balance")
                .executes(ctx -> { })
                .then(Cmd.literal("pay").description("Send money")
                        .then(Cmd.arg("target", Args.word())
                                .then(Cmd.arg("amount", Args.integer(1, 100))
                                        .flag(Flag.of("silent", 's'))
                                        .executes(ctx -> { }))))
                .then(Cmd.literal("secret").hidden().executes(ctx -> { }))
                .then(Cmd.literal("top").then(Cmd.arg("page", Args.integer()).optional(1).executes(ctx -> { })));

        List<Usage.Line> lines = Usage.lines(root, n -> true);
        assertThat(lines).extracting(Usage.Line::usage).containsExactly(
                "eco",
                "eco help [page]",
                "eco pay <target> <amount> [-s|--silent]",
                "eco top [page]");
        assertThat(lines.get(2).description()).isEqualTo("Send money");
        assertThat(Usage.pages(17, 8)).isEqualTo(3);
        assertThat(Usage.page(lines, 2, 3)).hasSize(1);
    }

    @Command(value = "kit", aliases = "kits", help = true)
    static final class KitCommand {
        enum Size { SMALL, LARGE }

        @Default
        @Description("List kits")
        public String list(CommandSender sender) {
            return "<gray>kits";
        }

        @Subcommand({"give", "g"})
        @Description("Give a kit")
        public void give(CommandSender sender, @Arg("name") String name, @Range(min = 1, max = 64) @Opt("1") int amount,
                         @com.enderbyte.enderlib.command.annotation.Flag(value = "silent", shortName = 's') boolean silent) {
        }

        @Subcommand("admin reset")
        public void reset(CommandSender sender, Size size) {
        }
    }

    @Test
    void annotatedClassBecomesATree() {
        LiteralNode root = AnnotatedCommands.parse(new KitCommand(), ParameterTypes.standard()).getFirst();
        assertThat(root.name()).isEqualTo("kit");
        assertThat(root.aliases()).containsExactly("kits");
        List<String> usages = Usage.lines(root, n -> true).stream().map(Usage.Line::usage).toList();
        assertThat(usages).contains(
                "kit",
                "kit give <name> [amount] [-s|--silent]",
                "kit admin reset <size>");
        LiteralNode give = (LiteralNode) root.children().stream().filter(c -> c.name().equals("give")).findFirst().orElseThrow();
        assertThat(give.aliases()).containsExactly("g");
    }

    @Command("broken")
    static final class Broken {
        @Subcommand("x")
        public void x(CommandSender sender, @Opt String a, String b) {
        }
    }

    @Test
    void rejectsRequiredAfterOptional() {
        assertThatThrownBy(() -> AnnotatedCommands.parse(new Broken(), ParameterTypes.standard()))
                .hasMessageContaining("required argument 'b' follows an optional one");
    }
}
