package com.bitwig.mods.quickinsert;

import bml.api.Bml;
import bml.panels.PanelProvider;
import bml.panels.Panels;

/**
 * Quick Insert's panel: one view, its icon and its content (the grid of slots). Every window gets
 * its own grid; the slots are read from the mod itself, not from the project.
 */
public final class QuickInsertPanels implements PanelProvider {
    static final Bml.Mod MOD = Bml.mod("quick-insert-slots");

    @Override
    public void declare(Panels panels) {
        // The id carries the mod id: Bitwig keeps a panel's place under it, so it is set once and
        // then left alone (a rename would lose everybody's layout).
        panels.view("quick_insert_slots_view", "Quick Insert")
              .icon("quick_insert_icon")
              .content(GridView::create);
    }
}
