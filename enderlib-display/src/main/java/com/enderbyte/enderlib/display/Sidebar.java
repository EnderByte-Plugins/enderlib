package com.enderbyte.enderlib.display;

import com.enderbyte.enderlib.core.Terminable;
import com.enderbyte.enderlib.text.Text;
import io.papermc.paper.scoreboard.numbers.NumberFormat;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.Criteria;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;

/**
 * A per-player sidebar with up to 15 lines and no score numbers.
 *
 * <pre>{@code
 * Sidebars.of(lib).create(player)
 *         .title(Text.mm("<gradient:gold:yellow><bold>MY SERVER"))
 *         .dynamicLines(p -> List.of(
 *                 Text.mm("<gray>Rank: <white>" + ranks.of(p)),
 *                 Component.empty(),
 *                 Text.mm("<gray>Coins: <gold>" + economy.balance(p))), 20)
 *         .show();
 * }</pre>
 *
 * <p>Only lines whose text changed are sent, so updating every tick does not flicker. The sidebar
 * uses its own scoreboard; whatever scoreboard the player had before is restored when it is hidden.
 * Scoreboards are not available on Folia.</p>
 */
public final class Sidebar implements Terminable {

    public static final int MAX_LINES = 15;

    private final Sidebars owner;
    private final Player player;
    private final Scoreboard scoreboard;
    private final Objective objective;
    private final List<@Nullable Component> shown = new ArrayList<>();
    private @Nullable Scoreboard previous;
    private @Nullable Function<Player, Component> dynamicTitle;
    private @Nullable Function<Player, List<Component>> dynamicLines;
    private int updateTicks;
    private @Nullable Terminable ticker;
    private boolean visible;
    private boolean closed;

    Sidebar(Sidebars owner, Player player) {
        this.owner = owner;
        this.player = player;
        this.scoreboard = Bukkit.getScoreboardManager().getNewScoreboard();
        this.objective = scoreboard.registerNewObjective("enderlib", Criteria.DUMMY, Component.empty());
        objective.numberFormat(NumberFormat.blank());
        objective.setDisplaySlot(DisplaySlot.SIDEBAR);
    }

    public Player player() {
        return player;
    }

    public Sidebar title(Component title) {
        objective.displayName(title);
        return this;
    }

    public Sidebar title(String miniMessage) {
        return title(Text.mm(miniMessage));
    }

    /** Replaces all lines (at most {@value #MAX_LINES}; extra lines are dropped). */
    public Sidebar lines(List<? extends Component> lines) {
        int count = Math.min(MAX_LINES, lines.size());
        for (int i = 0; i < count; i++) {
            setLine(i, lines.get(i), count);
        }
        for (int i = count; i < shown.size(); i++) {
            if (shown.get(i) != null) {
                scoreboard.resetScores(entry(i));
                shown.set(i, null);
            }
        }
        while (shown.size() > count) {
            shown.removeLast();
        }
        return this;
    }

    public Sidebar lines(Component... lines) {
        return lines(List.of(lines));
    }

    /** Sets one line (0 = top), extending the sidebar if needed. */
    public Sidebar line(int index, Component text) {
        if (index < 0 || index >= MAX_LINES) {
            throw new IndexOutOfBoundsException("Line " + index + " is outside 0.." + (MAX_LINES - 1));
        }
        List<Component> copy = new ArrayList<>();
        for (int i = 0; i < Math.max(shown.size(), index + 1); i++) {
            Component c = i < shown.size() ? shown.get(i) : null;
            copy.add(i == index ? text : c == null ? Component.empty() : c);
        }
        return lines(copy);
    }

    /** Title recomputed every {@code updateTicks}. */
    public Sidebar dynamicTitle(Function<Player, Component> title, int updateTicks) {
        this.dynamicTitle = Objects.requireNonNull(title, "title");
        return schedule(updateTicks);
    }

    /** Lines recomputed every {@code updateTicks}. */
    public Sidebar dynamicLines(Function<Player, List<Component>> lines, int updateTicks) {
        this.dynamicLines = Objects.requireNonNull(lines, "lines");
        return schedule(updateTicks);
    }

    /** Shows the sidebar to its player. */
    public Sidebar show() {
        if (closed || visible) {
            return this;
        }
        Scoreboard current = player.getScoreboard();
        previous = current == scoreboard ? Bukkit.getScoreboardManager().getMainScoreboard() : current;
        refresh();
        player.setScoreboard(scoreboard);
        visible = true;
        return this;
    }

    /** Hides it and restores the previous scoreboard; the sidebar can be shown again. */
    public Sidebar hide() {
        if (!visible) {
            return this;
        }
        visible = false;
        if (player.isOnline() && player.getScoreboard() == scoreboard) {
            player.setScoreboard(previous != null ? previous : Bukkit.getScoreboardManager().getMainScoreboard());
        }
        return this;
    }

    public boolean isVisible() {
        return visible;
    }

    /** Re-evaluates dynamic title and lines now. */
    public void refresh() {
        try {
            if (dynamicTitle != null) {
                Component t = dynamicTitle.apply(player);
                if (!t.equals(objective.displayName())) {
                    objective.displayName(t);
                }
            }
            if (dynamicLines != null) {
                lines(dynamicLines.apply(player));
            }
        } catch (Throwable t) {
            owner.lib().logger().error("Sidebar update for {} failed", player.getName(), t);
        }
    }

    /** Hides and disposes the sidebar. */
    @Override
    public void close() {
        if (closed) {
            return;
        }
        closed = true;
        if (ticker != null) {
            ticker.close();
        }
        hide();
        objective.unregister();
        owner.forget(this);
    }

    @Override
    public boolean isClosed() {
        return closed;
    }

    private Sidebar schedule(int ticks) {
        this.updateTicks = Math.max(1, ticks);
        if (ticker == null) {
            ticker = owner.lib().ticker().subscribe(player, tick -> {
                if (visible && tick % updateTicks == 0) {
                    refresh();
                }
            });
        }
        return this;
    }

    private void setLine(int index, Component text, int count) {
        while (shown.size() <= index) {
            shown.add(null);
        }
        var score = objective.getScore(entry(index));
        int value = count - index;
        if (!text.equals(shown.get(index))) {
            score.customName(text);
            shown.set(index, text);
        }
        if (score.getScore() != value || !score.isScoreSet()) {
            score.setScore(value);
        }
    }

    /** Invisible, unique score holder per line; the visible text is the score's custom name. */
    private static String entry(int index) {
        return "§" + Integer.toHexString(index) + "§r";
    }
}
