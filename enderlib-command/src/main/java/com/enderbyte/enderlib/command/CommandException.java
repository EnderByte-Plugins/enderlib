package com.enderbyte.enderlib.command;

import com.enderbyte.enderlib.text.Text;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;

/**
 * Stops a command and shows {@link #message()} to the sender. Not logged: this is the normal way
 * to report "you can't do that".
 *
 * <pre>{@code
 * if (balance < amount) {
 *     throw ctx.fail("<red>You only have <gold><balance></gold>.", Placeholder.unparsed("balance", fmt(balance)));
 * }
 * }</pre>
 */
public class CommandException extends RuntimeException {

    private final transient Component message;

    public CommandException(Component message) {
        super(Text.plain(message), null, false, false);
        this.message = message;
    }

    public static CommandException of(String miniMessage, TagResolver... resolvers) {
        return new CommandException(Text.mm(miniMessage, resolvers));
    }

    public Component message() {
        return message;
    }
}
