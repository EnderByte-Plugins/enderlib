package com.enderbyte.enderlib.command;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * A fixed word: the command label itself or a subcommand such as {@code give}.
 */
public final class LiteralNode extends CommandNode<LiteralNode> {

    private static final Pattern NAME = Pattern.compile("[a-z0-9_.:-]+");

    final List<String> aliases = new ArrayList<>();
    boolean help;

    LiteralNode(String name) {
        super(name.toLowerCase(Locale.ROOT));
        if (!NAME.matcher(this.name).matches()) {
            throw new IllegalArgumentException("Invalid command word '" + name + "'");
        }
    }

    /** Alternative names. On a root command they become command aliases. */
    public LiteralNode aliases(String... aliases) {
        for (String a : aliases) {
            this.aliases.add(a.toLowerCase(Locale.ROOT));
        }
        return this;
    }

    /**
     * Adds a {@code help [page]} subcommand listing every path the sender may run, with
     * descriptions. Typically used on the root.
     */
    public LiteralNode withHelp() {
        this.help = true;
        return this;
    }

    public List<String> aliases() {
        return List.copyOf(aliases);
    }

    @Override
    String usageToken() {
        return name;
    }
}
