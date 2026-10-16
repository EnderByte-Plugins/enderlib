package com.enderbyte.enderlib.command;

/**
 * Body of a command. Throw {@link CommandException} (or {@code ctx.fail(...)}) to report a problem
 * to the sender; any other exception is logged and the sender sees a generic error.
 */
@FunctionalInterface
public interface CommandHandler {
    void handle(CommandContext ctx) throws Exception;
}
