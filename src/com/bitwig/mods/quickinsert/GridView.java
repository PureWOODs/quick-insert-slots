package com.bitwig.mods.quickinsert;

import bitwig.obf.aBW;
import bitwig.obf.dJH;
import bitwig.obf.dJI;
import bitwig.obf.dJM;
import bitwig.obf.dns;
import bitwig.obf.dpN;
import bitwig.obf.drX;
import bitwig.obf.drv;
import bitwig.obf.dve;
import bitwig.obf.duG;
import bitwig.obf.eeY;
import bitwig.obf.eoV;
import bitwig.obf.eoW;
import bitwig.obf.iok;
import bml.panels.ViewContext;
import com.bitwig.base.geom.tfK;
import com.bitwig.graphics.Hpk;
import com.bitwig.graphics.SYo;

/**
 * The Quick Insert grid: every slot a cell, eight to a row. A click inserts that slot's preset
 * into the first selected track of the window's project. A cell with no preset is drawn dimmer
 * and does nothing.
 *
 * <p>What each slot holds comes from the mod's settings, one set of {@code slot.N.*} keys; see
 * {@link Slots}.
 */
final class GridView extends dve implements drv {
    private static final int COLUMNS = 8;

    // Sizes in Bitwig's layout units (scaled with the UI).
    private static final long PAD = 10;
    private static final long HEADER = 30;
    private static final long GAP = 6;

    /** The widest a cell may be, in proportion to its height: a name reads well at about 2:1. */
    private static final double CELL_SHAPE = 2.0;

    private ViewContext context;
    private aBW window;

    /** The count last drawn, so the log says when the grid changed shape and nothing else. */
    private int cachedShown = -1;

    /** Redraws when a slot changes: something may have been stored, emptied or renamed elsewhere. */
    private final Runnable onSlotsChanged = this::jbi;

    private GridView() {
    }

    static GridView create(ViewContext context) {
        GridView view = new GridView();
        view.context = context;
        view.window = context.window() instanceof aBW project ? project : null;
        view.H_(); // Bitwig's initialisation
        Slots.onChanged(view.onSlotsChanged); // the slots are shared: follow them from here
        return view;
    }

    @Override
    public tfK mL_() {
        return tfK.cfM(); // zero: no minimum
    }

    @Override
    public tfK nj_() {
        return tfK.MRY(); // unbounded
    }

    @Override
    public dve MJ() {
        return this;
    }

    @Override
    public dns UZr() {
        return this;
    }

    @Override
    public void iy_() {
        Slots.removeChanged(onSlotsChanged); // the panel is going: stop drawing for it
        super.iy_();
    }

    @Override
    public void cfM(duG window) {
        jbi(); // the UI scale changed: repaint
    }

    @Override
    public void cfM(SYo g) {
        try {
            paint(g);
        } catch (Throwable failure) {
            QuickInsertPanels.MOD.log("Quick Insert could not draw", failure);
        }
    }

    private void paint(SYo g) {
        Hpk font = kI_().cfM(MrC(), this);
        iok text = e3y().cfM(this);
        double ascent = font.cfM().cfM();
        g.qoU(); // save
        g.cfM(font);
        eeY background = jl_().BAa().cfM("Grey 1");
        if (background != null) {
            g.MRY(0, 0, Sxo(), yiX());
            g.Hnt(background.cfM(this));
            g.wGc();
        }
        int shown = Slots.shown();
        Layout layout = layout(shown);
        if (shown != cachedShown) {
            cachedShown = shown;
            Slots.probe("panel painted with " + shown + " slots, " + layout.columns + "x" + layout.rows
                        + ", size " + Sxo() + "x" + yiX());
        }
        Slots.Slot[] slots = Slots.slots();
        drawHeader(g, font, text, shown);
        for (int index = 0; index < shown; index++) {
            int column = index % layout.columns;
            int row = index / layout.columns;
            double x = eLY(PAD) + column * layout.cellWidth;
            double y = eLY(PAD + HEADER) + row * layout.cellHeight;
            double width = Math.max(1, layout.cellWidth - eLY(GAP));
            double height = Math.max(1, layout.cellHeight - eLY(GAP));
            Slots.Slot slot = slots[index];
            boolean filled = slot.filled();
            g.MRY(x, y, width, height);
            g.Hnt(text.cfM(filled ? 0.16F : 0.07F));
            g.wGc();
            g.MRY(x, y, width, height);
            g.Hnt(text.cfM(filled ? 0.35F : 0.18F));
            g.IwV(1);
            g.hbP();
            g.cfM(x + eLY(8), y + ascent + eLY(5), String.format("%02d", index + 1),
                  text.cfM(filled ? 1F : 0.35F));
            if (!slot.name.isEmpty() && height > ascent + eLY(28)) {
                g.cfM(x + eLY(8), y + ascent + eLY(24), fit(slot.name, 16), text.cfM(filled ? 0.7F : 0.3F));
            }
        }
        g.ucQ(); // restore
    }

    /** How the grid fills the panel: as few columns as keep the cells a sensible shape. */
    private static final class Layout {
        int columns;
        int rows;
        double cellWidth;
        double cellHeight;
    }

    private Layout layout(int shown) {
        Layout layout = new Layout();
        double width = Math.max(1, Sxo() - 2 * eLY(PAD));
        double height = Math.max(1, yiX() - eLY(PAD + HEADER) - eLY(PAD));
        int limit = Math.max(1, Math.min(COLUMNS, shown));
        int best = 1;
        double bestCost = Double.MAX_VALUE;
        // Fill the panel both ways, and pick the column count whose cells come closest to the
        // shape a slot wants: wide enough for a name, tall enough to hit. Columns are tried from
        // one up, so a tie goes to the fewer columns, which means the larger cells.
        for (int columns = 1; columns <= limit; columns++) {
            int rows = (shown + columns - 1) / columns;
            double cellWidth = width / columns;
            double cellHeight = height / rows;
            double cost = Math.abs(Math.log((cellWidth / cellHeight) / CELL_SHAPE));
            if (cost < bestCost - 1e-9) {
                bestCost = cost;
                best = columns;
            }
        }
        layout.columns = best;
        layout.rows = (shown + best - 1) / best;
        layout.cellWidth = width / best;
        layout.cellHeight = height / layout.rows;
        return layout;
    }

    /** The line above the grid: how many slots are on show. */
    private void drawHeader(SYo g, Hpk font, iok text, int shown) {
        double baseline = eLY(PAD) + font.cfM().cfM() + eLY(2);
        g.cfM(eLY(PAD), baseline, "Quick Insert \u00b7 " + shown + " slots", text.cfM(0.85F));
    }
    private static String fit(String name, int length) {
        return name.length() <= length ? name : name.substring(0, length - 1) + "\u2026";
    }

    @Override
    public drX MRY(eoW button, double x, double y, eoV mods, int clicks) {
        boolean main = button == eoW.cfM; // eoW.cfM: the main button
        // Clearing a cell is one right-click away from the click that inserts, so it can be turned
        // off in the settings; when it is off, other buttons do nothing here rather than insert.
        if (!main && !Slots.clearsOnRightClick()) return null;
        try {
            int slot = slotAt(x, y);
            // The main button inserts; any other button on a cell empties it, which is the only
            // thing a second button can mean here.
            return slot > 0 ? new Click(slot, !main) : null;
        } catch (Throwable failure) {
            QuickInsertPanels.MOD.log("Quick Insert could not handle a click", failure);
            return null;
        }
    }

    /** The slot a point is in, or 0 outside the grid. */
    private int slotAt(double x, double y) {
        int shown = Slots.shown();
        Layout layout = layout(shown);
        double across = x - eLY(PAD);
        double down = y - eLY(PAD + HEADER);
        if (across < 0 || down < 0) return 0;
        int column = (int) Math.floor(across / layout.cellWidth);
        int row = (int) Math.floor(down / layout.cellHeight);
        if (column < 0 || column >= layout.columns || row < 0) return 0;
        int slot = row * layout.columns + column + 1;
        return slot <= shown ? slot : 0;
    }

    /**
     * A press on a cell: shows the cell as pressed, and inserts the slot when the mouse is
     * released. Inserting on the press stalls Bitwig: the mouse is still held, so the insertion
     * waits for a drag session that only ends with the release.
     */
    private final class Click extends drX {
        private final int slot;
        private final boolean clear;
        private boolean pressed;
        private boolean done;

        Click(int slot, boolean clear) {
            this.slot = slot;
            this.clear = clear;
        }

        @Override
        public void cfM(dJH press) {
            if (clear) {
                // On the press, not on the release: clearing has nothing to preview and waiting
                // for the release made it feel unresponsive.
                clearNow();
                return;
            }
            pressed = true;
            jbi(); // repaint: the cell is held
        }

        /** Empties the cell straight away. */
        private void clearNow() {
            if (done) return;
            done = true;
            Slots.probe("panel clear: slot " + slot);
            try {
                Slots.clear(slot);
            } catch (Throwable failure) {
                QuickInsertPanels.MOD.log("Quick Insert could not clear slot " + slot, failure);
            }
            Slots.changed(); // the cells show the slots: forget the copy and redraw
            jbi();
        }

        @Override
        public void cfM(dJM move) {
            // A click, not a drag.
        }

        @Override
        public void cfM(dJI release) {
            pressed = false;
            jbi();
            // Bitwig's own release ends the mouse gesture. Without it the gesture stays active
            // until Bitwig gives up on it, and the window ignores the mouse meanwhile.
            super.cfM(release);
            if (clear) return; // already emptied on the press
            if (done) return;
            done = true;
            Slots.probe("panel click: slot " + slot);
            Runnable insert = () -> {
                long started = System.nanoTime();
                try {
                    Slots.insertInto(window, slot);
                    Slots.probe("panel insert done: slot " + slot + " in "
                                + (System.nanoTime() - started) / 1_000_000 + " ms");
                } catch (Throwable failure) {
                    QuickInsertPanels.MOD.log("Quick Insert could not insert slot " + slot, failure);
                    Slots.probe("panel insert failed: slot " + slot + " after "
                                + (System.nanoTime() - started) / 1_000_000 + " ms: " + failure);
                }
            };
            try {
                // After this mouse event, not inside it: inserting while Bitwig still handles the
                // click stalls the UI until the click's gesture has timed out.
                if (dpN.cfM != null) dpN.cfM.runAtTheVeryEndOfCurrentEvent(insert);
                else insert.run();
            } catch (Throwable failure) {
                QuickInsertPanels.MOD.log("Quick Insert could not schedule slot " + slot, failure);
                insert.run();
            }
        }
        /** A cancel (Escape, or Bitwig ending the gesture): ends the gesture too. */
        @Override
        public void FzB() {
            pressed = false;
            jbi();
            super.FzB();
        }
    }
}

