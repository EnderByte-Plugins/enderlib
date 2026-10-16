# Menus

`enderlib-menu`.

A `Menu` is an immutable definition. Opening it creates a `MenuView` for that player with its own
slots, states and paginations. Any number of players can have the same menu open.

## Building

```java
Menu menu = Menu.chest(3)                      // or Menu.of(InventoryType.HOPPER)
        .id("main")
        .title("<dark_gray>Main menu")
        .pattern(
                "#########",
                "#  K W  #",
                "####X####")
        .ingredient('#', Button.filler(Material.GRAY_STAINED_GLASS_PANE))
        .ingredient('K', Button.opens(kitIcon, kitsMenu))     // back() returns here
        .ingredient('W', Button.of(warpIcon).onClick(ctx -> ctx.player().performCommand("warps")))
        .ingredient('X', Button.close(closeIcon))
        .build();

Menus.of(lib).open(player, menu);
```

Other builder options: `slot(index, button)`, `fill(button)`, `border(button)`, `setup(view -> ...)`
(per-player content), `onOpen`, `onClose`, `closeGuard` (veto Escape), `refreshEvery(ticks)`,
`clickDelay(ticks)` (anti auto-clicker, default 2), `lockPlayerInventory()`,
`closeOnOutsideClick()`, `returnInputs(boolean)`, `titleDecorator(...)`.

## Buttons

```java
Button.of(item).onClick(ctx -> ...)
        .onLeft(...).onRight(...).onShift(...).onMiddle(...).onDrop(...)
        .onClick(ClickType.NUMBER_KEY, ...)
        .sound(Sound.UI_BUTTON_CLICK)
        .cooldown(10)                                   // ticks, per viewer
        .permission("shop.vip", ctx -> ctx.player().sendMessage(...))
        .visibleIf(view -> ...)
        .updateEvery(20);                               // live clocks and stats

Button.dynamic(view -> ...)                             // computed per render
Button.animated(5, frame1, frame2, frame3)
Button.toggle(state, onItem, offItem)
Button.cycle(state, List.of(A, B, C), value -> icon(value))
Button.filler(Material.BLACK_STAINED_GLASS_PANE)
Button.close(icon) / Button.back(icon) / Button.opens(icon, menu)
```

## Reactive state

```java
.setup(view -> {
    State<Integer> amount = view.state(1);
    view.set(13, Button.dynamic(v -> ItemBuilder.of(Material.GOLD_INGOT, amount.get()).build()));
    view.set(12, Button.of(minus).onClick(c -> amount.update(a -> Math.max(1, a - 1))));
    view.set(14, Button.of(plus).onClick(c -> amount.update(a -> Math.min(64, a + 1))));
})
```

Any button or title that calls `state.get()` while rendering re-renders automatically when the state
changes — and nothing else does. Rendering is diffed: only items that actually changed are sent.

## Pagination and scrolling

```java
.paginate('.', PageSource.of(products), (product, view) -> Button.of(product.icon()).onClick(...))
.ingredient('<', PageButtons.previous(prevIcon, disabledIcon))
.ingredient('>', PageButtons.next(nextIcon, null))
.title("<gold>Shop <gray>(<page>/<pages>)")      // updates live
```

- `PageSource.of(list)`, `PageSource.of(supplier)` (re-read on reload), or `PageSource.async((offset, limit) -> future)` for database paging;
  `Page.unknownTotal(items, hasMore)` when counting is expensive.
- Layouts: `PageLayout.PAGES`, `SCROLL_VERTICAL` (one row per step), `SCROLL_HORIZONTAL` (one column per step).
- `Pagination` methods: `next`, `previous`, `first`, `last`, `goTo`, `reload`, `page`, `pages`,
  `hasNext`, `isLoading`, `loadingButton(...)`, `emptyButton(...)`. Outdated async responses are discarded.

## Input slots

```java
view.set(13, Button.input()
        .accept(item -> item.getType() == Material.DIAMOND)
        .maxAmount(64)
        .onChange((v, item) -> price.set(value(item))));
```

Shift-clicks route only into accepting input slots, drags are validated, double-click collection is
always blocked, and items left in input slots are returned on close.

## Navigation

`ctx.open(menu)` remembers the current menu; `ctx.back()` returns (as a fresh view) or closes.
`view.suspend()` / `view.resume()` hide and restore a view with its state intact.

## Text input

```java
TextInput.builder(Component.text("Rename your pet"))
        .initial(pet.getName()).maxLength(24)
        .validate(s -> !s.isBlank(), Component.text("Cannot be empty"))
        .prompt(view)                                  // suspends the menu, resumes afterwards
        .thenAccept(result -> result.ifPresent(name::set));
```

Uses Paper's native dialog screen; `.chat()` switches to a chat prompt.

## Templates

- `ConfirmMenu.builder(title).info(item).onConfirm(...).onCancel(...).build()`
- `SelectorMenu.players(title, (ctx, target) -> ...)`, `SelectorMenu.of(title, options, icon, onSelect)`, `SelectorMenu.enumValues(...)`
- `MenuSpec` + `MenuActions`: menus defined in YAML (bind with `Configs`), with actions such as
  `close`, `back`, `open:<id>`, `command:<cmd>`, `console:<cmd>`, `message:<mm>`, `sound:<key>`,
  `next-page`, and your own: `actions.register("buy", (ctx, arg) -> ...)`.
