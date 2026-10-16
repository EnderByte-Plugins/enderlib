package com.enderbyte.enderlib.display;

import com.enderbyte.enderlib.core.Terminable;
import com.enderbyte.enderlib.text.Text;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.JoinConfiguration;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.entity.Display;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * A text hologram built on a {@link TextDisplay} entity, optionally with an item above it.
 *
 * <pre>{@code
 * Hologram top = Holograms.of(lib).create(location)
 *         .line("<gold><bold>Top killers")
 *         .lines(leaderboard.lines())
 *         .item(ItemStack.of(Material.DIAMOND_SWORD))
 *         .spawn();
 *
 * // Different text for every viewer, refreshed every second
 * Holograms.of(lib).create(npcLocation)
 *         .perPlayer(p -> List.of(Text.mm("<yellow>Hi " + p.getName()), Text.mm("<gray>Quests: " + quests.open(p))), 20)
 *         .spawn();
 * }</pre>
 *
 * <p>Entities are not saved with the world; the hologram respawns them after chunk reloads.
 * Per-player holograms spawn one display per nearby viewer, visible only to that viewer.</p>
 */
public final class Hologram implements Terminable {

    private static final double VIEW_DISTANCE = 48;

    private final Holograms owner;
    private Location location;
    private final List<Component> lines = new ArrayList<>();
    private @Nullable Function<Player, List<Component>> perPlayer;
    private int updateTicks;
    private Display.Billboard billboard = Display.Billboard.CENTER;
    private boolean shadow;
    private boolean seeThrough;
    private @Nullable Color background;
    private float scale = 1f;
    private float viewRange = 1f;
    private int lineWidth = 200;
    private @Nullable Predicate<Player> visibleTo;
    private @Nullable ItemStack item;
    private float itemScale = 0.6f;

    private @Nullable TextDisplay shared;
    private @Nullable ItemDisplay itemEntity;
    private final Map<UUID, TextDisplay> personal = new HashMap<>();
    private final Set<UUID> shownTo = new HashSet<>();
    private @Nullable Terminable ticker;
    private boolean spawned;
    private boolean closed;

    Hologram(Holograms owner, Location location) {
        this.owner = owner;
        this.location = location.clone();
    }

    // ================================================================== configuration

    public Hologram line(String miniMessage) {
        return line(Text.mm(miniMessage));
    }

    public Hologram line(Component line) {
        lines.add(line);
        return applyText();
    }

    public Hologram lines(List<? extends Component> newLines) {
        lines.clear();
        lines.addAll(newLines);
        return applyText();
    }

    /** Per-viewer text, recomputed every {@code updateTicks}. Replaces shared lines. */
    public Hologram perPlayer(Function<Player, List<Component>> content, int updateTicks) {
        if (spawned) {
            throw new IllegalStateException("Choose per-player mode before spawn()");
        }
        this.perPlayer = Objects.requireNonNull(content, "content");
        this.updateTicks = Math.max(1, updateTicks);
        return this;
    }

    public Hologram billboard(Display.Billboard billboard) {
        this.billboard = billboard;
        return this;
    }

    public Hologram shadow(boolean shadow) {
        this.shadow = shadow;
        return this;
    }

    /** Visible through blocks. */
    public Hologram seeThrough(boolean seeThrough) {
        this.seeThrough = seeThrough;
        return this;
    }

    /** Background colour (ARGB); {@code null} for the default translucent box. */
    public Hologram background(@Nullable Color background) {
        this.background = background;
        return this;
    }

    public Hologram scale(float scale) {
        this.scale = scale;
        return this;
    }

    /** View range multiplier (1 = 64 blocks at default entity distance). */
    public Hologram viewRange(float viewRange) {
        this.viewRange = viewRange;
        return this;
    }

    public Hologram lineWidth(int pixels) {
        this.lineWidth = pixels;
        return this;
    }

    /** Restricts who can see the hologram; re-evaluated every second. */
    public Hologram visibleTo(Predicate<Player> condition) {
        this.visibleTo = condition;
        return this;
    }

    /** An item floating above the text. */
    public Hologram item(@Nullable ItemStack item) {
        this.item = item == null ? null : item.clone();
        if (itemEntity != null) {
            ItemDisplay entity = itemEntity;
            onRegion(() -> entity.setItemStack(this.item));
        }
        return this;
    }

    public Hologram itemScale(float scale) {
        this.itemScale = scale;
        return this;
    }

    // ================================================================== lifecycle

    /** Spawns the entities. */
    public Hologram spawn() {
        if (closed) {
            throw new IllegalStateException("Hologram was removed");
        }
        if (spawned) {
            return this;
        }
        spawned = true;
        onRegion(this::spawnEntities);
        ticker = owner.lib().ticker().subscribe(tick -> {
            if (tick % 20 == 0) {
                onRegion(this::maintain);
            }
            if (perPlayer != null && tick % updateTicks == 0) {
                onRegion(this::updatePersonal);
            }
        });
        return this;
    }

    public Location location() {
        return location.clone();
    }

    /** Moves the hologram (smoothly, over {@code teleportTicks}). */
    public void teleport(Location to, int teleportTicks) {
        Location old = location;
        this.location = to.clone();
        onRegion(() -> {
            for (Display d : displays()) {
                d.setTeleportDuration(Math.max(0, Math.min(59, teleportTicks)));
                d.teleportAsync(d == itemEntity ? itemLocation() : location);
            }
        });
        if (old.getWorld() != to.getWorld()) {
            respawn();
        }
    }

    public void teleport(Location to) {
        teleport(to, 0);
    }

    /** Removes and spawns the entities again (after changing appearance settings). */
    public void respawn() {
        onRegion(() -> {
            removeEntities();
            if (spawned && !closed) {
                spawnEntities();
            }
        });
    }

    public boolean isSpawned() {
        return spawned && !closed;
    }

    /** Removes the hologram permanently. */
    @Override
    public void close() {
        if (closed) {
            return;
        }
        closed = true;
        if (ticker != null) {
            ticker.close();
        }
        onRegion(this::removeEntities);
        owner.forget(this);
    }

    @Override
    public boolean isClosed() {
        return closed;
    }

    void forgetViewer(UUID id) {
        TextDisplay d = personal.remove(id);
        if (d != null) {
            onRegion(d::remove);
        }
        shownTo.remove(id);
    }

    // ================================================================== entities

    private void spawnEntities() {
        if (!location.isChunkLoaded()) {
            return; // maintain() spawns once the chunk loads
        }
        if (perPlayer == null) {
            shared = location.getWorld().spawn(location, TextDisplay.class, d -> {
                configure(d);
                d.text(joined(lines));
                d.setVisibleByDefault(visibleTo == null);
            });
            shownTo.clear();
        }
        if (item != null) {
            itemEntity = location.getWorld().spawn(itemLocation(), ItemDisplay.class, d -> {
                d.setPersistent(false);
                d.setBillboard(Display.Billboard.VERTICAL);
                d.setItemStack(item);
                d.setTransformation(new Transformation(new Vector3f(), new AxisAngle4f(),
                        new Vector3f(itemScale, itemScale, itemScale), new AxisAngle4f()));
                d.setVisibleByDefault(visibleTo == null);
            });
        }
        maintain();
    }

    private void configure(TextDisplay d) {
        d.setPersistent(false);
        d.setBillboard(billboard);
        d.setShadowed(shadow);
        d.setSeeThrough(seeThrough);
        d.setLineWidth(lineWidth);
        d.setViewRange(viewRange);
        if (background != null) {
            d.setBackgroundColor(background);
        } else {
            d.setDefaultBackground(true);
        }
        if (scale != 1f) {
            d.setTransformation(new Transformation(new Vector3f(), new AxisAngle4f(), new Vector3f(scale, scale, scale), new AxisAngle4f()));
        }
    }

    /** Respawns after chunk reloads, and applies {@link #visibleTo} and per-player range. */
    private void maintain() {
        if (closed || !location.isChunkLoaded()) {
            return;
        }
        if (perPlayer == null && (shared == null || !shared.isValid())) {
            removeEntities();
            spawnEntities();
            return;
        }
        if (item != null && (itemEntity == null || !itemEntity.isValid())) {
            removeEntities();
            spawnEntities();
            return;
        }
        if (visibleTo != null) {
            for (Player p : Bukkit.getOnlinePlayers()) {
                boolean should = p.getWorld() == location.getWorld() && visibleTo.test(p);
                boolean is = shownTo.contains(p.getUniqueId());
                if (should != is) {
                    for (Display d : displays()) {
                        if (d == shared || d == itemEntity) {
                            if (should) {
                                p.showEntity(owner.lib().plugin(), d);
                            } else {
                                p.hideEntity(owner.lib().plugin(), d);
                            }
                        }
                    }
                    if (should) {
                        shownTo.add(p.getUniqueId());
                    } else {
                        shownTo.remove(p.getUniqueId());
                    }
                }
            }
        }
        if (perPlayer != null) {
            updatePersonal();
        }
    }

    private void updatePersonal() {
        if (closed || perPlayer == null || !location.isChunkLoaded()) {
            return;
        }
        Set<UUID> inRange = new HashSet<>();
        for (Player p : location.getWorld().getPlayers()) {
            if (p.getLocation().distanceSquared(location) > VIEW_DISTANCE * VIEW_DISTANCE
                    || visibleTo != null && !visibleTo.test(p)) {
                continue;
            }
            inRange.add(p.getUniqueId());
            Component text;
            try {
                text = joined(perPlayer.apply(p));
            } catch (Throwable t) {
                owner.lib().logger().error("Hologram content for {} failed", p.getName(), t);
                continue;
            }
            TextDisplay d = personal.get(p.getUniqueId());
            if (d == null || !d.isValid()) {
                d = location.getWorld().spawn(location, TextDisplay.class, e -> {
                    configure(e);
                    e.setVisibleByDefault(false);
                });
                personal.put(p.getUniqueId(), d);
                p.showEntity(owner.lib().plugin(), d);
            }
            if (!text.equals(d.text())) {
                d.text(text);
            }
        }
        personal.entrySet().removeIf(e -> {
            if (!inRange.contains(e.getKey())) {
                e.getValue().remove();
                return true;
            }
            return false;
        });
    }

    private void removeEntities() {
        for (Display d : displays()) {
            d.remove();
        }
        shared = null;
        itemEntity = null;
        personal.clear();
        shownTo.clear();
    }

    private List<Display> displays() {
        List<Display> out = new ArrayList<>(personal.values());
        if (shared != null) {
            out.add(shared);
        }
        if (itemEntity != null) {
            out.add(itemEntity);
        }
        return out;
    }

    private Hologram applyText() {
        if (shared != null) {
            TextDisplay d = shared;
            Component text = joined(lines);
            onRegion(() -> d.text(text));
        }
        return this;
    }

    private Location itemLocation() {
        int count = Math.max(1, lines.size());
        return location.clone().add(0, count * 0.27 * scale + 0.35, 0);
    }

    private static Component joined(List<? extends Component> lines) {
        return Component.join(JoinConfiguration.newlines(), lines);
    }

    private void onRegion(Runnable task) {
        owner.lib().scheduler().region(location).execute(() -> {
            try {
                task.run();
            } catch (Throwable t) {
                owner.lib().logger().error("Hologram task failed", t);
            }
        });
    }
}
