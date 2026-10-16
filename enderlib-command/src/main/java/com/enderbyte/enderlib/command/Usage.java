package com.enderbyte.enderlib.command;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

/**
 * Generates usage lines from a command tree. Pure: visibility is decided by the caller.
 */
public final class Usage {

    /** One runnable path, for example {@code eco pay <target> <amount> [-s|--silent]}. */
    public record Line(String usage, String description) {
    }

    private Usage() {
    }

    /** Every runnable path below and including {@code root}, in tree order. */
    public static List<Line> lines(LiteralNode root, Predicate<CommandNode<?>> visible) {
        List<Line> out = new ArrayList<>();
        collect(root, "", visible, out, "");
        return out;
    }

    private static void collect(CommandNode<?> node, String prefix, Predicate<CommandNode<?>> visible, List<Line> out,
                                String inheritedDescription) {
        if (node.hidden || !visible.test(node)) {
            return;
        }
        String path = prefix.isEmpty() ? node.usageToken() : prefix + " " + node.usageToken();
        String description = node.description.isEmpty() ? inheritedDescription : node.description;
        if (node.handler != null) {
            StringBuilder sb = new StringBuilder(path);
            for (Flag<?> f : node.flags) {
                sb.append(' ').append(f.usage());
            }
            out.add(new Line(sb.toString(), description));
        }
        if (node instanceof LiteralNode literal && literal.help && prefix.isEmpty()) {
            out.add(new Line(path + " help [page]", "Shows this help"));
        }
        for (CommandNode<?> child : node.children) {
            // Literal descriptions describe their own subtree; arguments inherit the literal's description
            collect(child, path, visible, out, child instanceof ArgumentNode<?> ? description : "");
        }
    }

    /** Lines of a 1-based page. */
    public static List<Line> page(List<Line> lines, int page, int perPage) {
        int from = Math.max(0, (page - 1) * perPage);
        if (from >= lines.size()) {
            return List.of();
        }
        return lines.subList(from, Math.min(lines.size(), from + perPage));
    }

    public static int pages(int lines, int perPage) {
        return Math.max(1, (lines + perPage - 1) / perPage);
    }
}
