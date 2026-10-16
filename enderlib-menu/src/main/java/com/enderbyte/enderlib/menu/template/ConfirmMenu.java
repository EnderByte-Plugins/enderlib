package com.enderbyte.enderlib.menu.template;

import com.enderbyte.enderlib.item.ItemBuilder;
import com.enderbyte.enderlib.menu.Button;
import com.enderbyte.enderlib.menu.ClickContext;
import com.enderbyte.enderlib.menu.Menu;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.jspecify.annotations.Nullable;

import java.util.Objects;
import java.util.function.Consumer;

/**
 * A yes/no menu.
 *
 * <pre>{@code
 * ConfirmMenu.builder(Component.text("Delete your island?"))
 *         .info(ItemBuilder.of(Material.GRASS_BLOCK).name("<red>This cannot be undone").build())
 *         .onConfirm(ctx -> { islands.delete(ctx.player()); ctx.close(); })
 *         .build();
 * }</pre>
 *
 * Cancel goes {@link ClickContext#back() back} by default, so a confirm opened from another menu
 * returns to it.
 */
public final class ConfirmMenu {

    private final Component title;
    private ItemStack confirmIcon = ItemBuilder.of(Material.LIME_STAINED_GLASS_PANE).name("<green><bold>Confirm").build();
    private ItemStack cancelIcon = ItemBuilder.of(Material.RED_STAINED_GLASS_PANE).name("<red><bold>Cancel").build();
    private @Nullable ItemStack info;
    private Button background = Button.filler(Material.GRAY_STAINED_GLASS_PANE);
    private Consumer<ClickContext> onConfirm = ClickContext::close;
    private Consumer<ClickContext> onCancel = ClickContext::back;

    private ConfirmMenu(Component title) {
        this.title = Objects.requireNonNull(title, "title");
    }

    public static ConfirmMenu builder(Component title) {
        return new ConfirmMenu(title);
    }

    public ConfirmMenu confirmIcon(ItemStack icon) {
        this.confirmIcon = icon.clone();
        return this;
    }

    public ConfirmMenu cancelIcon(ItemStack icon) {
        this.cancelIcon = icon.clone();
        return this;
    }

    /** Item shown in the middle explaining what is being confirmed. */
    public ConfirmMenu info(@Nullable ItemStack info) {
        this.info = info == null ? null : info.clone();
        return this;
    }

    public ConfirmMenu background(Button background) {
        this.background = background;
        return this;
    }

    public ConfirmMenu onConfirm(Consumer<ClickContext> handler) {
        this.onConfirm = Objects.requireNonNull(handler, "handler");
        return this;
    }

    public ConfirmMenu onCancel(Consumer<ClickContext> handler) {
        this.onCancel = Objects.requireNonNull(handler, "handler");
        return this;
    }

    public Menu build() {
        Menu.Builder b = Menu.chest(3)
                .id("confirm")
                .title(title)
                .pattern(
                        "#########",
                        "#YYYIXXX#",
                        "#########")
                .ingredient('#', background)
                .ingredient('Y', Button.of(confirmIcon).onClick(onConfirm))
                .ingredient('X', Button.of(cancelIcon).onClick(onCancel))
                .ingredient('I', info == null ? background : Button.of(info));
        return b.build();
    }
}
