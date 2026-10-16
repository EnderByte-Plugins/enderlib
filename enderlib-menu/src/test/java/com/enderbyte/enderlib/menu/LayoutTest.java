package com.enderbyte.enderlib.menu;

import com.enderbyte.enderlib.menu.layout.SlotPattern;
import com.enderbyte.enderlib.menu.layout.Slots;
import com.enderbyte.enderlib.menu.pagination.PageLayout;
import com.enderbyte.enderlib.menu.pagination.PageMath;
import com.enderbyte.enderlib.menu.template.MenuActions;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LayoutTest {

    @Test
    void patternMapsCharactersToSlotsInReadingOrder() {
        SlotPattern p = SlotPattern.of(
                "#########",
                "#..A....#",
                "#<##X##>#");
        assertThat(p.slots('A')).containsExactly(12);
        assertThat(p.slots('.')).containsExactly(10, 11, 13, 14, 15, 16);
        assertThat(p.slots('<')).containsExactly(19);
        assertThat(p.slots('?')).isEmpty();
        assertThat(p.charAt(22)).contains('X');
        assertThat(p.rows()).isEqualTo(3);
    }

    @Test
    void patternRejectsRowsWiderThanTheMenu() {
        assertThatThrownBy(() -> SlotPattern.of("##########"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("10 characters wide");
        assertThat(SlotPattern.of(5, "ABCDE").slots('E')).containsExactly(4);
    }

    @Test
    void slotGeometry() {
        assertThat(Slots.border(3)).hasSize(20).contains(0, 8, 9, 17, 18, 26).doesNotContain(10);
        assertThat(Slots.inner(3)).containsExactly(10, 11, 12, 13, 14, 15, 16);
        assertThat(Slots.rect(1, 1, 2, 2)).containsExactly(10, 11, 19, 20);
        assertThat(Slots.columnMajor(new int[] {10, 11, 19, 20}, 9)).containsExactly(10, 19, 11, 20);
        assertThat(Slots.bounds(Slots.rect(1, 1, 4, 7), 9)).containsExactly(7, 4);
    }

    @Test
    void pageMath() {
        assertThat(PageMath.pages(0, 10, 10)).isEqualTo(1);
        assertThat(PageMath.pages(10, 10, 10)).isEqualTo(1);
        assertThat(PageMath.pages(11, 10, 10)).isEqualTo(2);
        assertThat(PageMath.pages(21, 10, 10)).isEqualTo(3);
        assertThat(PageMath.pages(-1, 10, 10)).isEqualTo(-1);
        // scrolling a 7x4 area one row (7 items) at a time over 50 items: positions 0,7,...,22 -> 4 + 1
        assertThat(PageMath.pages(50, 28, 7)).isEqualTo(5);
        assertThat(PageMath.page(14, 7)).isEqualTo(3);
        assertThat(PageMath.offsetOf(99, 21, 10, 10)).isEqualTo(20);
        assertThat(PageMath.offsetOf(0, 21, 10, 10)).isZero();
        assertThat(PageMath.hasNext(10, 21, 10, false)).isTrue();
        assertThat(PageMath.hasNext(20, 21, 10, false)).isFalse();
        assertThat(PageMath.hasNext(20, -1, 10, true)).isTrue();
    }

    @Test
    void pageLayoutsOrderAndStep() {
        int[] area = Slots.rect(1, 1, 2, 3); // 3 wide, 2 high
        assertThat(PageLayout.PAGES.step(area, 9)).isEqualTo(6);
        assertThat(PageLayout.SCROLL_VERTICAL.step(area, 9)).isEqualTo(3);
        assertThat(PageLayout.SCROLL_HORIZONTAL.step(area, 9)).isEqualTo(2);
        assertThat(PageLayout.SCROLL_HORIZONTAL.order(area, 9)).containsExactly(10, 19, 11, 20, 12, 21);
    }

    @Test
    void menuActionsRejectUnknownNames() {
        MenuActions actions = MenuActions.create();
        assertThat(actions.compile(List.of("close", "message:<green>hi"))).isNotNull();
        assertThatThrownBy(() -> actions.compile(List.of("explode")))
                .hasMessageContaining("Unknown menu action 'explode'");
    }
}
