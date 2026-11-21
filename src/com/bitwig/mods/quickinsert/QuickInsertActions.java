package com.bitwig.mods.quickinsert;

import java.io.File;

import bml.actions.ActionContext;
import bml.actions.Actions;
import bml.actions.ActionProvider;
import bml.actions.Menu;
import bml.actions.Submenu;
import bitwig.obf.CH;
import bitwig.obf.FAu;
import bitwig.obf.aBW;
import bitwig.obf.dve;
import bitwig.obf.jsd;

/**
 * The mod's entries in Bitwig's Actions and Menus framework: 64 slots that insert their content,
 * 64 that take a browser item into a slot, 64 that empty one, and one that fills the first slot
 * with nothing in it.
 *
 * <p>Bitwig keeps the name an action was registered with for as long as it runs: menus, the
 * Commander and the shortcut settings all show that one name. So a slot's name here is its
 * number, and only its number. The panel is the live view: its cells read the slots each time they
 * are drawn, so what is in a slot changes there at once.
 */
public final class QuickInsertActions implements ActionProvider {

    private static final int SLOTS = Slots.COUNT;

    @Override
    public void declare(Actions actions) {
        actions.category("quick_insert", "Quick Insert");

        Submenu slots = actions.submenu("quick_insert_menu", "Quick Insert")
                               .in(Menu.DEVICE);

        for (int number = 1; number <= SLOTS; number++) {
            final int slot = number;
            slots.action("quick_insert_slots_" + slot, insertLabel(slot))
                 // Only the slots on show take a menu row; hidden slots keep their settings and
                 // their shortcut, and come back unchanged. {@link Slots#shown()} is a field read.
                 .visibleWhen(context -> slot <= Slots.shown())
                 .onRun(context -> runSlot(slot, context));
        }

        // The other half: a browser item can be taken into a slot from its right-click menu, so a
        // slot can be filled with what Bitwig's browser offers without knowing any file path.
        Submenu store = actions.submenu("quick_insert_store_menu", "Quick Insert")
                               .in(Menu.BROWSER_ITEM);

        // One click instead of picking a number: whatever was right-clicked goes into the first
        // slot on show with nothing in it. It sits outside the submenu, next to it, because it is
        // the shortcut the numbered list is the long way round to.
        actions.action("quick_insert_store_next", "Add to Next Empty Slot")
               .in(Menu.BROWSER_ITEM)
               .category("quick_insert")
               .visibleWhen(context -> firstEmptySlot() != 0)
               .onRun(context -> {
                   int empty = firstEmptySlot();
                   if (empty != 0) storeToSlot(empty, context);
               });

        for (int number = 1; number <= SLOTS; number++) {
            final int slot = number;
            store.action("quick_insert_store_" + slot, saveLabel(slot))
                 .category("quick_insert")
                 .visibleWhen(context -> slot <= Slots.shown())
                 .onRun(context -> storeToSlot(slot, context));
        }

        // A slot can be emptied from the same menu that fills it, and only a slot with something
        // in it offers that: an empty slot needs no way to empty it.
        for (int number = 1; number <= SLOTS; number++) {
            final int slot = number;
            store.action("quick_insert_clear_" + slot, String.format("Clear Slot %02d", slot))
                 .category("quick_insert")
                 .visibleWhen(context -> slot <= Slots.shown() && !Slots.nameOfSlot(slot).isEmpty())
                 .onRun(context -> Slots.clear(slot));
        }
    }

    /**
     * What the shortcut list and the device menu call a slot: its number, and nothing else.
     *
     * <p>A name is deliberately not part of it. Bitwig fixes an action's name when it is registered,
     * so a name would be the one the slot had at the start and would go stale the moment the slot
     * changed; the panel shows what is in each slot, live, which is where it is looked for.
     */
    private static String insertLabel(int slot) {
        return String.format("Slot %02d", slot);
    }

    /** What a browser item's menu calls a slot: saving into it. */
    private static String saveLabel(int slot) {
        return String.format("Save to Slot %02d", slot);
    }

    private static void runSlot(int slot, ActionContext context) {
        try {
            Slots.Slot content = Slots.slot(slot);
            Slots.probe("slot " + slot + " start: device=" + describe(context.device())
                        + " widget=" + describe(context.widget())
                        + " path=" + content.path);
            if (!content.filled()) {
                Slots.probe("SLOT_EMPTY: slot " + slot + " has nothing configured");
                Slots.notify("Quick Insert: slot " + slot + " is empty");
                return;
            }
            if (context.device() instanceof jsd device) {
                FAu chain = Slots.chainOf(device);
                if (chain != null) {
                    Slots.insert(chain, content); // the chain the right-clicked device is in
                    return;
                }
            }
            Slots.insertInto(windowOf(context.widget()), slot);
        } catch (Throwable problem) {
            Slots.probe("SLOT_ERROR: " + problem);
            Slots.notify("Quick Insert: slot " + slot + " failed (" + problem.getClass().getSimpleName() + ")");
        }
    }

    /** The project window a widget belongs to. */
    private static aBW windowOf(Object widget) {
        if (!(widget instanceof dve view)) return null;
        Object found = view.TMF(aBW.class); // TMF(Class): this widget or the one above it
        if (found instanceof aBW window) return window;
        return (aBW) view.lYc(aBW.class).findFirst().orElse(null);
    }

    /** Takes the browser item the menu was opened on into a slot. */
    private static void storeToSlot(int slot, ActionContext context) {
        Object item = context.item();
        Slots.probe("store start: slot " + slot + " item=" + describe(item));
        if (!(item instanceof CH result)) {
            Slots.probe("STORE_UNSUPPORTED: " + describe(item) + " is not a browser result");
            Slots.notify("Quick Insert: that is not something the browser gives a file for");
            return;
        }
        File file = result.Grk(); // Grk(): the file behind a result, for the results that have one
        String name = result.wGc(); // wGc(): the name the browser shows
        String path = file != null ? file.getPath() : "";
        if (path.isEmpty()) {
            try {
                com.bitwig.base.io.z2y own = result.tp2(); // tp2(): Bitwig's own path
                if (own != null) path = own.toString();
            } catch (Throwable ignored) {
                // No path either way.
            }
        }
        if (path.isEmpty()) {
            Slots.probe("STORE_NO_PATH: " + name);
            Slots.notify("Quick Insert: " + name + " has no file to point a slot at");
            return;
        }
        // The store keeps the module inside a bundle as the path, and the bundle as where it came
        // from, so a slot made from a VST3 bundle still opens.
        Slots.store(slot, path, path, name);
        Slots.probe("STORE_DONE: slot " + slot + " = " + Slots.cleanName(name) + " (" + Slots.moduleOf(path) + ")");
        Slots.notify("Quick Insert: slot " + slot + " = " + Slots.cleanName(name));
    }

    private static int firstEmptySlot() {
        int limit = Slots.shown();
        for (int slot = 1; slot <= limit; slot++) {
            if (Slots.nameOfSlot(slot).isEmpty()) return slot;
        }
        return 0;
    }

    private static String describe(Object value) {
        return value == null ? "null" : value.getClass().getName();
    }
}
