package com.enderbyte.enderlib.menu.input;

import com.enderbyte.enderlib.core.EnderLib;
import com.enderbyte.enderlib.menu.MenuView;
import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.event.player.AsyncChatEvent;
import io.papermc.paper.registry.data.dialog.ActionButton;
import io.papermc.paper.registry.data.dialog.DialogBase;
import io.papermc.paper.registry.data.dialog.action.DialogAction;
import io.papermc.paper.registry.data.dialog.body.DialogBody;
import io.papermc.paper.registry.data.dialog.input.DialogInput;
import io.papermc.paper.registry.data.dialog.type.DialogType;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickCallback;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventPriority;
import org.jspecify.annotations.Nullable;

import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Predicate;

/**
 * Asks a player for a line of text using a native dialog screen (or chat, as a fallback).
 *
 * <pre>{@code
 * TextInput.builder(Component.text("Rename your pet"))
 *         .label(Component.text("Name"))
 *         .initial(pet.getName())
 *         .maxLength(24)
 *         .validate(s -> !s.isBlank(), Component.text("The name cannot be empty"))
 *         .prompt(lib, player)
 *         .thenAccept(result -> result.ifPresent(pet::setName));   // runs on the player's thread
 * }</pre>
 *
 * When called with a {@link MenuView}, the menu is suspended while the player types and resumed
 * afterwards with all its state intact.
 */
public final class TextInput {

    private final Component title;
    private Component label = Component.text("Text");
    private String initial = "";
    private int maxLength = 256;
    private boolean multiline;
    private Component confirm = Component.text("Confirm");
    private Component cancel = Component.text("Cancel");
    private @Nullable Component body;
    private Predicate<String> validator = s -> true;
    private @Nullable Component invalidMessage;
    private Duration timeout = Duration.ofMinutes(5);
    private boolean useChat;

    private TextInput(Component title) {
        this.title = Objects.requireNonNull(title, "title");
    }

    public static TextInput builder(Component title) {
        return new TextInput(title);
    }

    public TextInput label(Component label) {
        this.label = label;
        return this;
    }

    public TextInput initial(String initial) {
        this.initial = initial;
        return this;
    }

    public TextInput maxLength(int maxLength) {
        this.maxLength = Math.max(1, maxLength);
        return this;
    }

    public TextInput multiline(boolean multiline) {
        this.multiline = multiline;
        return this;
    }

    public TextInput buttons(Component confirm, Component cancel) {
        this.confirm = confirm;
        this.cancel = cancel;
        return this;
    }

    /** Explanatory text shown above the field. */
    public TextInput body(Component body) {
        this.body = body;
        return this;
    }

    /** Rejects input not matching {@code validator}; the prompt re-opens with {@code message}. */
    public TextInput validate(Predicate<String> validator, @Nullable Component message) {
        this.validator = Objects.requireNonNull(validator, "validator");
        this.invalidMessage = message;
        return this;
    }

    /** How long the prompt waits before completing empty. Default five minutes. */
    public TextInput timeout(Duration timeout) {
        this.timeout = timeout;
        return this;
    }

    /** Uses the chat box instead of a dialog ({@code cancel} cancels). */
    public TextInput chat() {
        this.useChat = true;
        return this;
    }

    /**
     * Shows the prompt. The future completes on the player's thread with the text, or empty if the
     * player cancelled, timed out or logged out.
     */
    public CompletableFuture<Optional<String>> prompt(EnderLib lib, Player player) {
        CompletableFuture<Optional<String>> result = new CompletableFuture<>();
        lib.scheduler().runAt(player, () -> {
            if (useChat) {
                promptChat(lib, player, result);
            } else {
                promptDialog(lib, player, result, initial);
            }
        });
        return result;
    }

    /** Suspends {@code view}, prompts, then resumes the view before completing. */
    public CompletableFuture<Optional<String>> prompt(MenuView view) {
        view.suspend();
        return prompt(view.menus().lib(), view.viewer()).whenComplete((r, e) -> view.resume());
    }

    // ------------------------------------------------------------------ dialog

    private void promptDialog(EnderLib lib, Player player, CompletableFuture<Optional<String>> result, String start) {
        AtomicBoolean answered = new AtomicBoolean();
        ClickCallback.Options once = ClickCallback.Options.builder().uses(1).lifetime(timeout).build();

        ActionButton ok = ActionButton.builder(confirm).action(DialogAction.customClick((response, audience) -> {
            if (!answered.compareAndSet(false, true)) {
                return;
            }
            String text = Objects.requireNonNullElse(response.getText("text"), "");
            lib.scheduler().runAt(player, () -> {
                if (!validator.test(text)) {
                    if (invalidMessage != null) {
                        player.sendMessage(invalidMessage);
                    }
                    promptDialog(lib, player, result, text);
                } else {
                    result.complete(Optional.of(text));
                }
            });
        }, once)).build();

        ActionButton no = ActionButton.builder(cancel).action(DialogAction.customClick((response, audience) -> {
            if (answered.compareAndSet(false, true)) {
                lib.scheduler().runAt(player, () -> result.complete(Optional.empty()));
            }
        }, once)).build();

        var input = DialogInput.text("text", label).initial(start).maxLength(maxLength);
        if (multiline) {
            input.multiline(io.papermc.paper.registry.data.dialog.input.TextDialogInput.MultilineOptions.create(8, null));
        }
        DialogBase base = DialogBase.builder(title)
                .canCloseWithEscape(true)
                .afterAction(DialogBase.DialogAfterAction.CLOSE)
                .body(body == null ? List.of() : List.of(DialogBody.plainMessage(body)))
                .inputs(List.of(input.build()))
                .build();
        Dialog dialog = Dialog.create(factory -> factory.empty().base(base).type(DialogType.confirmation(ok, no)));
        player.showDialog(dialog);

        // Escape closes the dialog without a callback: fall back on the timeout
        lib.scheduler().runAsyncLater(() -> {
            if (answered.compareAndSet(false, true)) {
                lib.scheduler().runAt(player, () -> result.complete(Optional.empty()), () -> result.complete(Optional.empty()));
            }
        }, timeout);
    }

    // ------------------------------------------------------------------ chat

    private void promptChat(EnderLib lib, Player player, CompletableFuture<Optional<String>> result) {
        player.sendMessage(body == null ? title : title.appendNewline().append(body));
        lib.events().listen(AsyncChatEvent.class)
                .priority(EventPriority.LOWEST)
                .filter(e -> e.getPlayer().getUniqueId().equals(player.getUniqueId()))
                .expireAfter(timeout)
                .handler((sub, e) -> {
                    e.setCancelled(true);
                    String text = PlainTextComponentSerializer.plainText().serialize(e.message());
                    lib.scheduler().runAt(player, () -> {
                        if (text.equalsIgnoreCase("cancel")) {
                            sub.close();
                            result.complete(Optional.empty());
                        } else if (text.length() > maxLength || !validator.test(text)) {
                            if (invalidMessage != null) {
                                player.sendMessage(invalidMessage);
                            }
                        } else {
                            sub.close();
                            result.complete(Optional.of(text));
                        }
                    });
                });
        lib.scheduler().runAsyncLater(() -> lib.scheduler().runAt(player,
                () -> result.complete(Optional.empty()), () -> result.complete(Optional.empty())), timeout);
    }
}
