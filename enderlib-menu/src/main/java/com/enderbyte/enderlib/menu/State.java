package com.enderbyte.enderlib.menu;

import java.util.BitSet;
import java.util.Objects;
import java.util.function.UnaryOperator;

/**
 * A reactive value owned by one {@link MenuView}.
 *
 * <p>Any button (or the title) that calls {@link #get()} while rendering is recorded as depending on
 * this state. Changing the value re-renders exactly those slots, nothing else:</p>
 *
 * <pre>{@code
 * State<Integer> amount = view.state(1);
 * view.set(13, Button.dynamic(v -> ItemBuilder.of(Material.GOLD_INGOT).amount(amount.get()).build()));
 * view.set(12, Button.of(minus).onClick(c -> amount.update(a -> Math.max(1, a - 1))));
 * view.set(14, Button.of(plus).onClick(c -> amount.update(a -> Math.min(64, a + 1))));
 * }</pre>
 *
 * States must be read and written on the viewer's thread (the main thread on Paper).
 */
public final class State<T> {

    private final MenuView view;
    private final BitSet dependents = new BitSet();
    private boolean titleDependent;
    private T value;

    State(MenuView view, T initial) {
        this.view = view;
        this.value = initial;
    }

    /** Reads the value, registering the slot currently being rendered as a dependent. */
    public T get() {
        view.track(this);
        return value;
    }

    /** Reads without registering a dependency (for click handlers and logic). */
    public T peek() {
        return value;
    }

    /** Sets the value; dependent slots re-render if it changed. */
    public void set(T newValue) {
        if (Objects.equals(value, newValue)) {
            return;
        }
        value = newValue;
        view.invalidate(this);
    }

    public void update(UnaryOperator<T> fn) {
        set(fn.apply(value));
    }

    /** Re-renders dependents even if the value is unchanged (for mutable values such as lists). */
    public void touch() {
        view.invalidate(this);
    }

    BitSet dependents() {
        return dependents;
    }

    boolean titleDependent() {
        return titleDependent;
    }

    void addDependent(int slot) {
        if (slot == MenuView.TITLE) {
            titleDependent = true;
        } else if (slot >= 0) {
            dependents.set(slot);
        }
    }

    void clearDependent(int slot) {
        if (slot >= 0) {
            dependents.clear(slot);
        }
    }

    @Override
    public String toString() {
        return "State[" + value + "]";
    }
}
