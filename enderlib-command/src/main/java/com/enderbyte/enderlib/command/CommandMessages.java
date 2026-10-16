package com.enderbyte.enderlib.command;

import com.enderbyte.enderlib.text.Text;
import com.enderbyte.enderlib.util.Durations;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.command.CommandSender;

import java.time.Duration;

/**
 * Texts the command framework shows. Override any of them, for example to route through
 * {@code Messages} for translations:
 *
 * <pre>{@code
 * Commands.of(lib).messages(new CommandMessages() {
 *     public Component noPermission(CommandSender s) { return messages.get(s, "command.no-permission"); }
 * });
 * }</pre>
 */
public interface CommandMessages {

    CommandMessages DEFAULT = new CommandMessages() { };

    default Component noPermission(CommandSender sender) {
        return Text.mm("<red>You don't have permission to do that.");
    }

    default Component playerOnly(CommandSender sender) {
        return Text.mm("<red>Only players can use this command.");
    }

    default Component consoleOnly(CommandSender sender) {
        return Text.mm("<red>This command can only be run from the console.");
    }

    default Component cooldown(CommandSender sender, Duration remaining) {
        return Text.mm("<red>Please wait <white><time></white> before using this again.",
                Placeholder.unparsed("time", Durations.format(remaining)));
    }

    default Component confirm(CommandSender sender, String command, Duration window) {
        return Text.mm("<yellow>Run <white>/<command></white> again within <seconds>s to confirm.",
                Placeholder.unparsed("command", command), Placeholder.unparsed("seconds", String.valueOf(window.toSeconds())));
    }

    default Component flagError(CommandSender sender, String problem) {
        return Text.mm("<red><problem>", Placeholder.unparsed("problem", problem));
    }

    default Component internalError(CommandSender sender) {
        return Text.mm("<red>An internal error occurred. Please tell an administrator.");
    }

    default Component helpHeader(CommandSender sender, String command, int page, int pages) {
        return Text.mm("<gold>/<command> <gray>help <dark_gray>(<page>/<pages>)",
                Placeholder.unparsed("command", command), Placeholder.unparsed("page", String.valueOf(page)),
                Placeholder.unparsed("pages", String.valueOf(pages)));
    }

    default Component helpLine(CommandSender sender, String usage, String description) {
        return Text.mm(description.isEmpty() ? "<yellow>/<usage>" : "<yellow>/<usage> <dark_gray>- <gray><description>",
                Placeholder.unparsed("usage", usage), Placeholder.unparsed("description", description));
    }
}
