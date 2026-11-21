package com.bitwig.mods.quickinsert;

import bml.preferences.Page;
import bml.preferences.PreferenceProvider;
import bml.preferences.Preferences;
import bml.preferences.Section;

/**
 * The mod's controls in Bitwig's Settings: one section on the shared Mods page.
 *
 * <p>How many slots are on show, whether a click fills every selected track, and whether a
 * right-click empties one. A slot's content is set from Bitwig's browser or the device menu, where
 * the thing being stored is at hand, and the panel shows what is in each slot, so nothing here is
 * about names: Bitwig reads an action name once, at launch, and a menu cannot keep up with a slot
 * that changes.
 */
public final class QuickInsertSettings implements PreferenceProvider {

    /** The counts worth offering: a grid of 8 x 8 at most, and the shapes in between. */
    private static final int[] COUNTS = {4, 8, 16, 32, 48, 64};

    private static final String[] LABELS = {"4", "8", "16", "32", "48", "64"};

    @Override
    public void declare(Preferences preferences) {
        Page mods = preferences.page("mods", "Mods");
        Section mine = preferences.section(mods, "quick_insert", "Quick Insert");
        preferences.choice("quick_insert_slots_shown", "Slots on show", LABELS)
                   .in(mine)
                   .value(() -> indexOf(Slots.shown()),
                          index -> Slots.show(COUNTS[Math.max(0, Math.min(COUNTS.length - 1, index))]));
        preferences.checkbox("quick_insert_insert_all_selected", "Insert into all selected tracks")
                   .in(mine)
                   .value(Slots::insertsIntoAllSelected, Slots::insertsIntoAllSelected);
        preferences.checkbox("quick_insert_notify", "Show a notice when a slot is used")
                   .in(mine)
                   .value(Slots::notifies, Slots::notifies);
        preferences.checkbox("quick_insert_clear_on_right_click", "Right-click clears a slot")
                   .in(mine)
                   .value(Slots::clearsOnRightClick, Slots::clearsOnRightClick);
    }

    /** The row for a count: the one it equals, or the nearest one below it. */
    private static int indexOf(int count) {
        int best = 0;
        for (int index = 0; index < COUNTS.length; index++) {
            if (COUNTS[index] <= count) best = index;
            if (COUNTS[index] == count) return index;
        }
        return best;
    }
}
