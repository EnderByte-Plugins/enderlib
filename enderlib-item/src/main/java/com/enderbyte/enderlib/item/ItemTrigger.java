package com.enderbyte.enderlib.item;

/**
 * Interactions a {@link CustomItem} can react to.
 */
public enum ItemTrigger {
    /** Right-click in air or on a block (either hand). */
    RIGHT_CLICK,
    /** Right-click while sneaking. Fires instead of {@link #RIGHT_CLICK} when a handler exists. */
    SHIFT_RIGHT_CLICK,
    /** Left-click in air or on a block. */
    LEFT_CLICK,
    /** Left-click while sneaking. Fires instead of {@link #LEFT_CLICK} when a handler exists. */
    SHIFT_LEFT_CLICK,
    /** Right-click on an entity. */
    INTERACT_ENTITY,
    /** Melee hit on an entity while holding the item in the main hand. */
    HIT_ENTITY,
    /** Block broken while holding the item in the main hand. */
    BREAK_BLOCK,
    /** Item eaten or drunk. */
    CONSUME,
    /** Item dropped with Q. */
    DROP,
    /** Swapped to or from the off-hand with F. */
    SWAP_HANDS,
    /** Selected in the hotbar. */
    SELECT,
    /** Deselected in the hotbar. */
    DESELECT,
    /** Put into an armour slot. */
    EQUIP,
    /** Taken out of an armour slot. */
    UNEQUIP
}
